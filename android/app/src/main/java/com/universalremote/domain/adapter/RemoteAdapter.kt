package com.universalremote.domain.adapter

import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.RemoteCapability
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Eventos no-estado que un adaptador publica hacia el gestor. */
sealed interface AdapterEvent {
    /** El adaptador aprendió datos persistibles (MAC, modelo, token, huella TLS). */
    data class ProfileUpdated(val profile: DeviceProfile) : AdapterEvent
    data class KeyboardRequested(val initialText: String) : AdapterEvent
    data object KeyboardDismissed : AdapterEvent
}

/**
 * Estrategia por protocolo (patrón Adapter/Strategy).
 *
 * Contrato:
 * - [connect] es idempotente y puede llamarse de nuevo tras [disconnect] o un fallo.
 * - [send] lanza [RemoteError.NotConnected] si no hay sesión y el comando no puede despertar al equipo.
 * - Toda E/S ocurre fuera del hilo principal; los métodos son seguros desde cualquier dispatcher.
 * - [close] libera recursos de forma definitiva; el adaptador no se reutiliza después.
 */
interface RemoteAdapter {
    val profile: DeviceProfile
    val state: StateFlow<ConnectionState>
    val events: SharedFlow<AdapterEvent>
    val capabilities: Set<RemoteCapability>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun send(command: RemoteCommand)

    fun supports(command: RemoteCommand): Boolean = command.capability in capabilities

    suspend fun submitPairingCode(code: String) {
        throw RemoteError.UnsupportedCommand("PAIRING_CODE")
    }

    suspend fun close() = disconnect()
}

/** Construye el adaptador adecuado para un perfil. */
fun interface RemoteAdapterFactory {
    /** @throws RemoteError.UnsupportedDevice si no hay adaptador para [DeviceProfile.kind]. */
    fun create(profile: DeviceProfile): RemoteAdapter
}
