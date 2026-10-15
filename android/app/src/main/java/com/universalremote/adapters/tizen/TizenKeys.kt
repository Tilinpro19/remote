package com.universalremote.adapters.tizen

import com.universalremote.domain.model.RemoteCommand

/** Códigos `KEY_*` del canal `samsung.remote.control`. */
internal object TizenKeys {
    fun of(command: RemoteCommand): String? = when (command) {
        RemoteCommand.Power, RemoteCommand.PowerOff -> "KEY_POWER"
        RemoteCommand.VolumeUp -> "KEY_VOLUP"
        RemoteCommand.VolumeDown -> "KEY_VOLDOWN"
        RemoteCommand.Mute -> "KEY_MUTE"
        RemoteCommand.ChannelUp -> "KEY_CHUP"
        RemoteCommand.ChannelDown -> "KEY_CHDOWN"
        is RemoteCommand.Digit -> "KEY_${command.value}"
        RemoteCommand.DpadUp -> "KEY_UP"
        RemoteCommand.DpadDown -> "KEY_DOWN"
        RemoteCommand.DpadLeft -> "KEY_LEFT"
        RemoteCommand.DpadRight -> "KEY_RIGHT"
        RemoteCommand.DpadCenter, RemoteCommand.Enter -> "KEY_ENTER"
        RemoteCommand.Back -> "KEY_RETURN"
        RemoteCommand.Home -> "KEY_HOME"
        RemoteCommand.Menu -> "KEY_MENU"
        RemoteCommand.Info -> "KEY_INFO"
        RemoteCommand.Input -> "KEY_SOURCE"
        RemoteCommand.PlayPause, RemoteCommand.Play -> "KEY_PLAY"
        RemoteCommand.Pause -> "KEY_PAUSE"
        RemoteCommand.Stop -> "KEY_STOP"
        RemoteCommand.Rewind -> "KEY_REWIND"
        RemoteCommand.FastForward -> "KEY_FF"
        RemoteCommand.Backspace -> "KEY_BACKSPACE"
        is RemoteCommand.Raw -> command.key
        else -> null
    }
}
