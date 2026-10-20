package com.universalremote.domain.model

data class PairingRequest(val method: Method, val message: String) {
    sealed interface Method {
        /** El TV muestra un PIN que el usuario introduce en el móvil. */
        data class Pin(val length: Int) : Method

        /** Clave libre que el usuario configuró en el TV (p. ej. PSK de Sony); distingue mayúsculas. */
        data object Passphrase : Method

        /** El usuario acepta un diálogo en el TV. */
        data object ConfirmOnScreen : Method
    }
}

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState

    /** Wake-on-LAN enviado; esperando a que el equipo responda. */
    data object Waking : ConnectionState
    data class AwaitingPairing(val request: PairingRequest) : ConnectionState
    data object Connected : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState
    data class Failed(val error: RemoteError) : ConnectionState

    val isConnected: Boolean get() = this == Connected
    val isBusy: Boolean get() = this is Connecting || this is Waking || this is Reconnecting || this is AwaitingPairing
}
