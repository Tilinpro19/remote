package com.universalremote.adapters.panasonic

import com.universalremote.adapters.common.getText
import com.universalremote.adapters.common.postBody
import com.universalremote.core.di.LanHttpClient
import com.universalremote.domain.adapter.AdapterEvent
import com.universalremote.domain.adapter.RemoteAdapter
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.RemoteCapability
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import com.universalremote.network.wol.WakeOnLanSender
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient

/**
 * Panasonic Viera: SOAP `X_SendKey` sobre HTTP en el puerto 55000. Sin emparejamiento.
 *
 * Los modelos desde 2018 cifran los comandos (AES con clave obtenida por PIN) y anuncian
 * `X_GetEncryptSessionId` en `/nrc/sdd_0.xml`; esta versión los rechaza con
 * [RemoteError.UnsupportedDevice] (`PANASONIC_ENCRYPTED`) en vez de fallar en silencio.
 */
class VieraAdapter @AssistedInject constructor(
    @Assisted initialProfile: DeviceProfile,
    @LanHttpClient private val client: OkHttpClient,
    private val wakeOnLan: WakeOnLanSender,
) : RemoteAdapter {

    private val lan: DeviceTransport.Lan = initialProfile.transport as? DeviceTransport.Lan
        ?: throw IllegalArgumentException("VieraAdapter requiere DeviceTransport.Lan")

    private val baseUrl = "http://${lan.host}:${lan.port}/nrc"

    override val profile: DeviceProfile = initialProfile

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdapterEvent>()
    override val events: SharedFlow<AdapterEvent> = _events.asSharedFlow()

    private val mutex = Mutex()

    override val capabilities: Set<RemoteCapability> = setOf(
        RemoteCapability.POWER, RemoteCapability.VOLUME, RemoteCapability.CHANNELS,
        RemoteCapability.DIGITS, RemoteCapability.NAVIGATION, RemoteCapability.MEDIA,
    )

    override fun supports(command: RemoteCommand): Boolean =
        command.capability in capabilities && VieraKeys.keyFor(command) != null

    override suspend fun connect() = mutex.withLock {
        if (_state.value == ConnectionState.Connected) return@withLock
        _state.value = ConnectionState.Connecting
        try {
            if (client.getText("$baseUrl/sdd_0.xml").contains(ENCRYPTION_ACTION)) throw encrypted()
            _state.value = ConnectionState.Connected
        } catch (t: Throwable) {
            throw fail(t)
        }
    }

    override suspend fun disconnect() = mutex.withLock {
        _state.value = ConnectionState.Idle
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (_state.value != ConnectionState.Connected) {
            // Viera apagado no tiene red: sólo Wake-on-LAN (si el TV lo admite) puede encenderlo.
            val mac = profile.macAddress
            if (command.canWakeDevice && mac != null) {
                wakeOnLan.wake(mac, lastKnownHost = lan.host)
                return@withLock
            }
            throw RemoteError.NotConnected()
        }
        val key = VieraKeys.keyFor(command) ?: throw RemoteError.UnsupportedCommand(command.wireName)
        try {
            client.postBody("$baseUrl/control_0", envelope(key), mapOf("SOAPACTION" to SOAP_ACTION))
        } catch (e: RemoteError.PairingRejected) {
            throw fail(encrypted())
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            val error = RemoteError.from(t, lan.host)
            if (error !is RemoteError.Protocol) _state.value = ConnectionState.Failed(error)
            throw error
        }
        Unit
    }

    private fun encrypted() = RemoteError.UnsupportedDevice("PANASONIC_ENCRYPTED")

    private fun fail(t: Throwable): RemoteError {
        val error = RemoteError.from(t, lan.host)
        _state.value = ConnectionState.Failed(error)
        return error
    }

    private fun envelope(key: String) =
        """<?xml version="1.0" encoding="utf-8"?>""" +
            """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
            """<s:Body><u:X_SendKey xmlns:u="$SERVICE"><X_KeyEvent>$key</X_KeyEvent></u:X_SendKey></s:Body></s:Envelope>"""

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): VieraAdapter
    }

    private companion object {
        const val SERVICE = "urn:panasonic-com:service:p00NetworkControl:1"
        const val SOAP_ACTION = "\"$SERVICE#X_SendKey\""
        const val ENCRYPTION_ACTION = "X_GetEncryptSessionId"
    }
}
