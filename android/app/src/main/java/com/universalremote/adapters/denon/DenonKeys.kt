package com.universalremote.adapters.denon

import com.universalremote.domain.model.RemoteCommand

/**
 * Órdenes del protocolo de control Denon/Marantz (sin el CR final). Power y Mute no están aquí:
 * son alternancias que necesitan consultar el estado (ver [DenonAdapter.send]).
 */
object DenonKeys {

    fun commandFor(command: RemoteCommand): String? = when (command) {
        RemoteCommand.PowerOn -> "PWON"
        RemoteCommand.PowerOff -> "PWSTANDBY"
        RemoteCommand.VolumeUp -> "MVUP"
        RemoteCommand.VolumeDown -> "MVDOWN"
        RemoteCommand.DpadUp -> "MNCUP"
        RemoteCommand.DpadDown -> "MNCDN"
        RemoteCommand.DpadLeft -> "MNCLT"
        RemoteCommand.DpadRight -> "MNCRT"
        RemoteCommand.DpadCenter -> "MNENT"
        RemoteCommand.Back -> "MNRTN"
        RemoteCommand.Menu -> "MNMEN ON"
        RemoteCommand.Info -> "MNINF"
        // Reproductor de red (NS9x).
        RemoteCommand.Play -> "NS9A"
        RemoteCommand.Pause -> "NS9B"
        RemoteCommand.Stop -> "NS9C"
        RemoteCommand.Next -> "NS9D"
        RemoteCommand.Previous -> "NS9E"
        else -> null
    }
}
