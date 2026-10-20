package com.universalremote.network.discovery

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import android.util.Xml
import com.universalremote.core.di.IoDispatcher
import com.universalremote.core.di.LanHttpClient
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.DiscoveredDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser

/**
 * Descubrimiento híbrido SSDP (UDP multicast) + mDNS ([NsdManager]).
 *
 * El [WifiManager.MulticastLock] se adquiere sólo durante la ventana de escaneo y se libera al
 * completarse o cancelarse el `Flow`: mantenerlo desactiva el filtrado multicast del chip Wi-Fi
 * y penaliza la batería.
 */
@Singleton
class LanDiscoveryManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
    @LanHttpClient private val http: OkHttpClient,
) {
    private val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val nsdManager = context.getSystemService(NsdManager::class.java)

    /** Pre-API 34 `NsdManager.resolveService` sólo admite una resolución simultánea. */
    private val resolveMutex = Mutex()

    /**
     * Emite la lista acumulada y deduplicada por host cada vez que aparece o se enriquece un equipo.
     * Completa al cumplirse [window].
     */
    fun scan(window: Duration = DEFAULT_WINDOW): Flow<List<DiscoveredDevice>> = channelFlow {
        val lock = acquireMulticastLock()
        val registryMutex = Mutex()
        val found = LinkedHashMap<String, DiscoveredDevice>()

        val publish: suspend (DiscoveredDevice) -> Unit = { device ->
            val snapshot = registryMutex.withLock {
                val merged = found[device.key]?.mergeWith(device) ?: device
                if (found[device.key] == merged) null else {
                    found[device.key] = merged
                    found.values.toList()
                }
            }
            snapshot?.let { send(it) }
        }

        try {
            withTimeoutOrNull(window) {
                coroutineScope {
                    launch { runSsdp(publish) }
                    launch { runMdns(publish) }
                }
            }
        } finally {
            if (lock?.isHeld == true) lock.release()
        }
    }.flowOn(io)

    // ---------------------------------------------------------------- MulticastLock

    private fun acquireMulticastLock(): WifiManager.MulticastLock? = try {
        wifiManager?.createMulticastLock(LOCK_TAG)?.apply {
            setReferenceCounted(false)
            acquire()
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Sin CHANGE_WIFI_MULTICAST_STATE; el escaneo puede perder respuestas", e)
        null
    }

    // ---------------------------------------------------------------- SSDP

    private suspend fun runSsdp(publish: suspend (DiscoveredDevice) -> Unit) = coroutineScope {
        val socket = try {
            openSsdpSocket()
        } catch (e: IOException) {
            Log.w(TAG, "No se pudo abrir el socket SSDP", e)
            return@coroutineScope
        }
        val describedLocations = HashSet<String>()
        try {
            val group = InetSocketAddress(InetAddress.getByName(Ssdp.MULTICAST_ADDRESS), Ssdp.PORT)
            val buffer = ByteArray(SSDP_BUFFER)
            var nextSearchAt = 0L
            var searchesSent = 0

            while (currentCoroutineContext().isActive) {
                val now = System.nanoTime()
                if (searchesSent < SSDP_SEARCH_ROUNDS && now >= nextSearchAt) {
                    // UDP no garantiza entrega: varias rondas mejoran la tasa de detección.
                    for (target in Ssdp.SEARCH_TARGETS) {
                        val payload = Ssdp.searchRequest(target)
                        runCatching { socket.send(DatagramPacket(payload, payload.size, group)) }
                            .onFailure { Log.d(TAG, "Fallo enviando M-SEARCH $target", it) }
                    }
                    searchesSent++
                    nextSearchAt = now + SSDP_RESEND_INTERVAL.inWholeNanoseconds
                }

                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                val response = Ssdp.parse(text, packet.address.hostAddress ?: continue) ?: continue
                val kind = response.classify() ?: continue

                publish(
                    DiscoveredDevice(
                        host = response.host,
                        name = defaultName(kind, response.host),
                        kind = kind,
                        port = if (kind == DeviceKind.ROKU) response.locationPort ?: kind.defaultControlPort else kind.defaultControlPort,
                        serviceIds = setOfNotNull(response.usn),
                        sources = setOf(DiscoveredDevice.Source.SSDP),
                    ),
                )

                val location = response.location
                if (location != null && describedLocations.add(location)) {
                    launch {
                        fetchDescription(location)?.let { (friendlyName, model) ->
                            publish(
                                DiscoveredDevice(
                                    host = response.host,
                                    name = friendlyName ?: defaultName(kind, response.host),
                                    kind = kind,
                                    port = null,
                                    model = model,
                                    sources = setOf(DiscoveredDevice.Source.SSDP),
                                ),
                            )
                        }
                    }
                }
            }
        } finally {
            socket.close()
        }
    }

    private fun openSsdpSocket(): MulticastSocket {
        val socket = MulticastSocket(null).apply {
            reuseAddress = true
            soTimeout = SSDP_RECEIVE_TIMEOUT_MS
            timeToLive = SSDP_TTL
            bind(InetSocketAddress(0))
        }
        // Si el teléfono tiene datos móviles activos, el multicast podría salir por la interfaz
        // celular: se fija explícitamente la red Wi-Fi/Ethernet.
        lanNetwork()?.let { network ->
            runCatching { network.bindSocket(socket) }
            connectivityManager?.getLinkProperties(network)?.interfaceName
                ?.let { runCatching { NetworkInterface.getByName(it) }.getOrNull() }
                ?.let { runCatching { socket.networkInterface = it } }
        }
        return socket
    }

    @Suppress("DEPRECATION")
    private fun lanNetwork(): Network? {
        val cm = connectivityManager ?: return null
        fun Network.isLan(): Boolean = cm.getNetworkCapabilities(this)?.let {
            it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } ?: false
        cm.activeNetwork?.takeIf { it.isLan() }?.let { return it }
        return cm.allNetworks.firstOrNull { it.isLan() }
    }

    /** Lee `friendlyName` y `modelName` del descriptor UPnP (Roku lo sirve en `LOCATION`). */
    private fun fetchDescription(location: String): Pair<String?, String?>? = try {
        val request = Request.Builder().url(location).get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.byteStream() ?: return null
            val parser = Xml.newPullParser().apply { setInput(body, null) }
            var friendlyName: String? = null
            var model: String? = null
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                when (parser.name) {
                    "friendlyName" -> if (friendlyName == null) friendlyName = parser.nextText().trim().ifEmpty { null }
                    "modelName" -> if (model == null) model = parser.nextText().trim().ifEmpty { null }
                }
                if (friendlyName != null && model != null) break
            }
            friendlyName to model
        }
    } catch (e: Exception) {
        // URL inválida, IOException o XML malformado: el equipo sigue siendo válido sin nombre amigable.
        Log.d(TAG, "Descriptor UPnP ilegible en $location", e)
        null
    }

    // ---------------------------------------------------------------- mDNS / NSD

    private suspend fun runMdns(publish: suspend (DiscoveredDevice) -> Unit) = coroutineScope {
        val nsd = nsdManager ?: return@coroutineScope
        val foundServices = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val listeners = ArrayList<NsdManager.DiscoveryListener>()

        try {
            for (type in MDNS_SERVICE_TYPES.keys) {
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onServiceFound(info: NsdServiceInfo) {
                        foundServices.trySend(info)
                    }
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        Log.w(TAG, "mDNS $serviceType no iniciado (error $errorCode)")
                    }
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
                    override fun onDiscoveryStarted(serviceType: String) = Unit
                    override fun onDiscoveryStopped(serviceType: String) = Unit
                    override fun onServiceLost(info: NsdServiceInfo) = Unit
                }
                try {
                    nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                    listeners += listener
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "No se pudo registrar descubrimiento $type", e)
                }
            }

            for (service in foundServices) {
                ensureActive()
                val resolved = resolve(nsd, service) ?: continue
                toDiscoveredDevice(resolved)?.let { publish(it) }
            }
        } finally {
            foundServices.close()
            for (listener in listeners) {
                try {
                    nsd.stopServiceDiscovery(listener)
                } catch (_: IllegalArgumentException) {
                    // Listener ya detenido por el sistema.
                }
            }
        }
    }

    private suspend fun resolve(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
        withTimeoutOrNull(RESOLVE_TIMEOUT) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                resolveWithCallback(nsd, info)
            } else {
                resolveMutex.withLock { resolveLegacy(nsd, info) }
            }
        }

    @SuppressLint("NewApi")
    private suspend fun resolveWithCallback(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? {
        var registered: NsdManager.ServiceInfoCallback? = null
        try {
            return suspendCancellableCoroutine { cont ->
                val callback = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
                        if (serviceInfo.hostAddresses.isNotEmpty() && cont.isActive) cont.resume(serviceInfo)
                    }
                    override fun onServiceLost() {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onServiceInfoCallbackUnregistered() = Unit
                }
                try {
                    nsd.registerServiceInfoCallback(info, io.asExecutor(), callback)
                    registered = callback
                } catch (e: IllegalArgumentException) {
                    cont.resume(null)
                }
            }
        } finally {
            // El callback sigue vivo tras la primera actualización: se libera siempre.
            registered?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolveLegacy(nsd: NsdManager, info: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { cont ->
            try {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        if (cont.isActive) cont.resume(null)
                    }
                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (cont.isActive) cont.resume(serviceInfo)
                    }
                })
            } catch (e: IllegalArgumentException) {
                if (cont.isActive) cont.resume(null)
            }
        }

    @Suppress("DEPRECATION")
    private fun toDiscoveredDevice(info: NsdServiceInfo): DiscoveredDevice? {
        val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses.firstOrNull { it is Inet4Address } ?: info.hostAddresses.firstOrNull()
        } else {
            info.host
        } ?: return null
        val host = address.hostAddress ?: return null
        val type = MDNS_SERVICE_TYPES.entries.firstOrNull { info.serviceType.contains(it.key.removeSuffix(".")) }
        val kind = type?.value
        val attributes = info.attributes.orEmpty()
        fun txt(key: String) = attributes[key]?.toString(Charsets.UTF_8)?.trim()?.ifEmpty { null }

        return DiscoveredDevice(
            host = host,
            name = txt("fn") ?: info.serviceName ?: defaultName(kind, host),
            kind = kind,
            port = kind?.defaultControlPort ?: info.port.takeIf { it > 0 },
            model = txt("md"),
            serviceIds = setOfNotNull(info.serviceName?.let { "${info.serviceType}/$it" }),
            sources = setOf(DiscoveredDevice.Source.MDNS),
        )
    }

    private fun defaultName(kind: DeviceKind?, host: String): String = when (kind) {
        DeviceKind.ROKU -> "Roku ($host)"
        DeviceKind.LG_WEBOS -> "LG webOS ($host)"
        DeviceKind.SAMSUNG_TIZEN -> "Samsung ($host)"
        DeviceKind.ANDROID_TV -> "Android TV ($host)"
        DeviceKind.SONY_BRAVIA -> "Sony Bravia ($host)"
        DeviceKind.VIZIO -> "Vizio ($host)"
        DeviceKind.PHILIPS_JOINTSPACE -> "Philips ($host)"
        DeviceKind.FIRE_TV -> "Fire TV ($host)"
        DeviceKind.HISENSE_VIDAA -> "Hisense ($host)"
        DeviceKind.PANASONIC_VIERA -> "Panasonic ($host)"
        DeviceKind.YAMAHA_MUSICCAST -> "Yamaha ($host)"
        DeviceKind.DENON_AVR -> "Denon/Marantz ($host)"
        DeviceKind.KODI -> "Kodi ($host)"
        DeviceKind.INFRARED, DeviceKind.BLUETOOTH_HID, null -> host
    }

    private companion object {
        const val TAG = "LanDiscovery"
        const val LOCK_TAG = "UniversalRemote:discovery"
        val DEFAULT_WINDOW = 8.seconds
        val RESOLVE_TIMEOUT = 3.seconds
        val SSDP_RESEND_INTERVAL = 1_500.milliseconds
        const val SSDP_SEARCH_ROUNDS = 3
        const val SSDP_RECEIVE_TIMEOUT_MS = 300
        const val SSDP_TTL = 4
        const val SSDP_BUFFER = 2_048

        /** Tipo mDNS → clase de equipo (`null` = sólo informativo, p. ej. Cast sin remoto ATV). */
        val MDNS_SERVICE_TYPES: Map<String, DeviceKind?> = linkedMapOf(
            "_androidtvremote2._tcp." to DeviceKind.ANDROID_TV,
            "_googlecast._tcp." to null,
            "_webostv._tcp." to DeviceKind.LG_WEBOS,
            "_amzn-wplay._tcp." to DeviceKind.FIRE_TV,
            "_xbmc-jsonrpc-h._tcp." to DeviceKind.KODI,
        )
    }
}

/** Fusiona anuncios del mismo host: conserva el dato más específico de cada fuente. */
internal fun DiscoveredDevice.mergeWith(other: DiscoveredDevice): DiscoveredDevice {
    val genericName = name == host || name.endsWith("($host)")
    // Un mismo TV puede anunciar dos protocolos (p. ej. Sony o Philips con Android TV): gana el más completo.
    val mergedKind = if (other.kind.precedence() > kind.precedence()) other.kind else kind
    return copy(
        name = if (genericName && !(other.name == host || other.name.endsWith("($host)"))) other.name else name,
        kind = mergedKind,
        port = if (mergedKind != kind) other.port ?: port else port ?: other.port,
        model = model ?: other.model,
        serviceIds = serviceIds + other.serviceIds,
        sources = sources + other.sources,
    )
}

/** Android TV Remote v2 admite teclado y puntero: se prefiere a IRCC (Sony) y JointSpace (Philips). */
private fun DeviceKind?.precedence(): Int = when (this) {
    null -> 0
    DeviceKind.ANDROID_TV -> 2
    else -> 1
}
