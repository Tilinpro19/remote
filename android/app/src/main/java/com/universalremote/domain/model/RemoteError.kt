package com.universalremote.domain.model

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
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
    class Timeout(cause: Throwable? = null) : RemoteError("El dispositivo no respondió a tiempo", cause)
    class Unreachable(host: String, cause: Throwable? = null) : RemoteError("No se alcanza $host", cause)
    class Network(cause: Throwable) : RemoteError("Error de red: ${cause.message ?: cause.javaClass.simpleName}", cause)
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
                Unreachable(host ?: t.message.orEmpty(), t)
            is IOException -> Network(t)
            is SecurityException -> PermissionDenied(t.message ?: "desconocido", t)
            else -> Protocol(t.message ?: t.javaClass.simpleName, t)
        }
    }
}
