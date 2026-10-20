package com.universalremote.adapters.vizio

import com.universalremote.domain.model.RemoteCommand

/**
 * Pares `(CODESET, CODE)` de la API SmartCast (`PUT /key_command/`), según la tabla que usan
 * los clientes abiertos (pyvizio). Vizio no expone dígitos ni botones de color por esta vía.
 */
object VizioKeys {

    fun codeFor(command: RemoteCommand): Pair<Int, Int>? = when (command) {
        RemoteCommand.Power -> 11 to 2
        RemoteCommand.PowerOn -> 11 to 1
        RemoteCommand.PowerOff -> 11 to 0
        RemoteCommand.VolumeDown -> 5 to 0
        RemoteCommand.VolumeUp -> 5 to 1
        RemoteCommand.Mute -> 5 to 4
        RemoteCommand.ChannelDown -> 8 to 0
        RemoteCommand.ChannelUp -> 8 to 1
        RemoteCommand.DpadDown -> 3 to 0
        RemoteCommand.DpadLeft -> 3 to 1
        RemoteCommand.DpadCenter -> 3 to 2
        RemoteCommand.DpadRight -> 3 to 7
        RemoteCommand.DpadUp -> 3 to 8
        RemoteCommand.Back -> 4 to 0
        RemoteCommand.Info -> 4 to 6
        RemoteCommand.Menu -> 4 to 8
        RemoteCommand.Home -> 4 to 15
        RemoteCommand.Input -> 7 to 1
        RemoteCommand.FastForward -> 2 to 0
        RemoteCommand.Rewind -> 2 to 1
        RemoteCommand.Pause -> 2 to 2
        RemoteCommand.Play -> 2 to 3
        else -> null
    }
}
