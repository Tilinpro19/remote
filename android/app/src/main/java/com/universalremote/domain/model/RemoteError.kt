package com.universalremote.domain.model

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException

/** Errores tipados que la capa de presentación puede mostrar sin inspeccionar excepciones de plataforma. */
sealed class RemoteError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class NotConnected : RemoteError("No hay sesión activa con el dispositivo")
    class NoActiveDevice : RemoteError("No hay ningún dispositivo seleccionado")
    class UnsupportedCommand(val command: String) : RemoteError("Comando no soportado: $command")
    class UnsupportedDevice(val kind: String) : RemoteError("Tipo de dispositivo no soportado: $kind")
    class HardwareUnavailable(reason: String) : RemoteError(reason)
    class PermissionDenied(val permission: String, cause: Throwable? = null) :
        RemoteError("Permiso requerido: $permission", cause)
    // Los mensajes van tal cual a la UI: breves, accionables y sin el texto crudo de la excepción
    // (los de OpenSSL/Conscrypt son ilegibles); la causa queda en `cause` para logs.
    class Timeout(cause: Throwable? = null) : RemoteError("Tiempo de espera agotado", cause)
    class Unreachable(val host: String, cause: Throwable? = null) : RemoteError("TV apagada o fuera de red", cause)
    class Network(cause: Throwable) : RemoteError("Se perdió la conexión con el TV", cause)
    class Tls(cause: Throwable) : RemoteError("Error de vinculación SSL (toca Reintentar o vuelve a vincular)", cause)
    class Protocol(message: String, cause: Throwable? = null) : RemoteError(message, cause)
    class InvalidCode(message: String) : RemoteError(message)
    class PairingRejected : RemoteError("El dispositivo rechazó el emparejamiento")

    companion object {
        /** Normaliza cualquier fallo a [RemoteError]; re-lanza la cancelación de corrutinas. */
        fun from(t: Throwable, host: String? = null): RemoteError = when (t) {
            is CancellationException -> throw t
            is RemoteError -> t
            is SocketTimeoutException -> Timeout(t)
            is ConnectException, is NoRouteToHostException, is UnknownHostException ->
                Unreachable(host.orEmpty(), t)
            is SSLException -> Tls(t)
            is IOException -> Network(t)
            is SecurityException -> PermissionDenied(t.message ?: "desconocido", t)
            else -> Protocol("Respuesta inesperada del dispositivo", t)
        }
    }
}
