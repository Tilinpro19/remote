package com.universalremote.adapters.tizen

import com.universalremote.domain.model.KeyColor
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.StreamingApp

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
        is RemoteCommand.ColorKey -> when (command.color) {
            KeyColor.RED -> "KEY_RED"
            KeyColor.GREEN -> "KEY_GREEN"
            KeyColor.YELLOW -> "KEY_YELLOW"
            KeyColor.BLUE -> "KEY_CYAN"
        }
        is RemoteCommand.Raw -> command.key
        else -> null
    }
}

/** appId de Smart Hub para el evento `ed.apps.launch`. */
internal object TizenApps {
    fun id(app: StreamingApp): String? = when (app) {
        StreamingApp.NETFLIX -> "3201907018807"
        StreamingApp.YOUTUBE -> "111299001912"
        StreamingApp.PRIME_VIDEO -> "3201910019365"
        StreamingApp.DISNEY_PLUS -> "3201901017640"
        StreamingApp.SPOTIFY -> "3201606009684"
        StreamingApp.APPLE_TV -> "3201807016597"
        StreamingApp.SAMSUNG_TV_PLUS -> "3201710015037"
        StreamingApp.BROWSER -> "org.tizen.browser"
        StreamingApp.ROKU_CHANNEL, StreamingApp.LG_CHANNELS -> null
    }
}
