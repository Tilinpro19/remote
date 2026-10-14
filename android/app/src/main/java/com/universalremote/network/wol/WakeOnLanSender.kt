package com.universalremote.network.wol

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.universalremote.core.di.IoDispatcher
import com.universalremote.domain.model.MacAddress
import com.universalremote.domain.model.RemoteError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Envío de Magic Packets Wake-on-LAN por UDP broadcast. */
@Singleton
class WakeOnLanSender @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    /**
     * Envía el paquete a la dirección de broadcast dirigida de la LAN actual, a `255.255.255.255`
     * y, si se conoce, en unicast a [lastKnownHost] (la caché ARP del router suele conservarla).
     * Repite [bursts] veces porque UDP no garantiza entrega y algunas NIC ignoran el primer paquete.
     *
     * @throws RemoteError.Network si no se pudo emitir ningún datagrama.
     */
    suspend fun wake(mac: MacAddress, lastKnownHost: String? = null, bursts: Int = 3) = withContext(io) {
        val payload = magicPacket(mac)
        val targets = buildList {
            directedBroadcast()?.let(::add)
            add(InetAddress.getByName(LIMITED_BROADCAST))
            lastKnownHost?.let { host -> runCatching { InetAddress.getByName(host) }.getOrNull()?.let(::add) }
        }.distinct()

        var sent = 0
        var lastError: IOException? = null
        DatagramSocket().use { socket ->
            socket.broadcast = true
            repeat(bursts) { burst ->
                for (target in targets) {
                    for (port in PORTS) {
                        try {
                            socket.send(DatagramPacket(payload, payload.size, target, port))
                            sent++
                        } catch (e: IOException) {
                            lastError = e
                            Log.d(TAG, "WoL a ${target.hostAddress}:$port falló", e)
                        }
                    }
                }
                if (burst < bursts - 1) delay(BURST_INTERVAL_MS)
            }
        }
        if (sent == 0) throw RemoteError.Network(lastError ?: IOException("No se pudo enviar el Magic Packet"))
    }

    /** Broadcast dirigido (`ip | ~máscara`) de la red Wi-Fi/Ethernet activa. */
    private fun directedBroadcast(): InetAddress? {
        val cm = connectivityManager ?: return null
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) return null
        val link = cm.getLinkProperties(network)?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address } ?: return null
        return broadcastFor(link.address.address, link.prefixLength)
    }

    companion object {
        private const val TAG = "WakeOnLan"
        private const val LIMITED_BROADCAST = "255.255.255.255"
        private val PORTS = intArrayOf(9, 7)
        private const val BURST_INTERVAL_MS = 100L
        private const val SYNC_BYTES = 6
        private const val MAC_REPETITIONS = 16

        /** 6 × `0xFF` + 16 × MAC = 102 bytes. */
        fun magicPacket(mac: MacAddress): ByteArray {
            val macBytes = mac.toBytes()
            val packet = ByteArray(SYNC_BYTES + MAC_REPETITIONS * macBytes.size)
            packet.fill(0xFF.toByte(), 0, SYNC_BYTES)
            for (i in 0 until MAC_REPETITIONS) {
                macBytes.copyInto(packet, SYNC_BYTES + i * macBytes.size)
            }
            return packet
        }

        fun broadcastFor(ipv4: ByteArray, prefixLength: Int): InetAddress {
            require(ipv4.size == 4 && prefixLength in 0..32)
            val ip = ipv4.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
            val mask = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)
            val broadcast = ip or mask.inv()
            return InetAddress.getByAddress(ByteArray(4) { i -> (broadcast ushr (24 - 8 * i)).toByte() })
        }
    }
}
