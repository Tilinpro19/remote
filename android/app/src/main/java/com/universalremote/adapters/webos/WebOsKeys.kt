package com.universalremote.adapters.webos

import com.universalremote.domain.model.RemoteCommand

/** Nombres de botón del socket "pointer input" de webOS. */
internal object WebOsKeys {
    fun button(command: RemoteCommand): String? = when (command) {
        RemoteCommand.DpadUp -> "UP"
        RemoteCommand.DpadDown -> "DOWN"
        RemoteCommand.DpadLeft -> "LEFT"
        RemoteCommand.DpadRight -> "RIGHT"
        RemoteCommand.DpadCenter -> "ENTER"
        RemoteCommand.Back -> "BACK"
        RemoteCommand.Home -> "HOME"
        RemoteCommand.Menu -> "MENU"
        RemoteCommand.Info -> "INFO"
        RemoteCommand.Mute -> "MUTE"
        is RemoteCommand.Digit -> command.value.toString()
        is RemoteCommand.Raw -> command.key
        else -> null
    }
}
