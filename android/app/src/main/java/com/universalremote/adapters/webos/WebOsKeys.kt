package com.universalremote.adapters.webos

import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.StreamingApp

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
        is RemoteCommand.ColorKey -> command.color.name
        is RemoteCommand.Raw -> command.key
        else -> null
    }
}

/** Identificadores de app para `ssap://system.launcher/launch`. */
internal object WebOsApps {
    fun id(app: StreamingApp): String? = when (app) {
        StreamingApp.NETFLIX -> "netflix"
        StreamingApp.YOUTUBE -> "youtube.leanback.v4"
        StreamingApp.PRIME_VIDEO -> "amazon"
        StreamingApp.DISNEY_PLUS -> "com.disney.disneyplus-prod"
        StreamingApp.SPOTIFY -> "spotify-beehive"
        StreamingApp.APPLE_TV -> "com.apple.appletv"
        StreamingApp.BROWSER -> "com.webos.app.browser"
        StreamingApp.LG_CHANNELS -> "com.webos.app.lgchannels"
        StreamingApp.ROKU_CHANNEL, StreamingApp.SAMSUNG_TV_PLUS -> null
    }
}
