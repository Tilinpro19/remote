package com.universalremote.adapters.androidtv

import android.view.KeyEvent
import com.universalremote.adapters.common.PointerAccumulator
import com.universalremote.domain.model.KeyColor
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.StreamingApp

/** Android TV inyecta `KeyEvent.KEYCODE_*` estándar. */
internal object AndroidTvKeys {
    fun of(command: RemoteCommand): Int? = when (command) {
        RemoteCommand.Power, RemoteCommand.PowerOn, RemoteCommand.PowerOff -> KeyEvent.KEYCODE_POWER
        RemoteCommand.VolumeUp -> KeyEvent.KEYCODE_VOLUME_UP
        RemoteCommand.VolumeDown -> KeyEvent.KEYCODE_VOLUME_DOWN
        RemoteCommand.Mute -> KeyEvent.KEYCODE_VOLUME_MUTE
        RemoteCommand.ChannelUp -> KeyEvent.KEYCODE_CHANNEL_UP
        RemoteCommand.ChannelDown -> KeyEvent.KEYCODE_CHANNEL_DOWN
        is RemoteCommand.Digit -> KeyEvent.KEYCODE_0 + command.value
        RemoteCommand.DpadUp -> KeyEvent.KEYCODE_DPAD_UP
        RemoteCommand.DpadDown -> KeyEvent.KEYCODE_DPAD_DOWN
        RemoteCommand.DpadLeft -> KeyEvent.KEYCODE_DPAD_LEFT
        RemoteCommand.DpadRight -> KeyEvent.KEYCODE_DPAD_RIGHT
        RemoteCommand.DpadCenter, RemoteCommand.TouchTap -> KeyEvent.KEYCODE_DPAD_CENTER
        RemoteCommand.Back -> KeyEvent.KEYCODE_BACK
        RemoteCommand.Home -> KeyEvent.KEYCODE_HOME
        RemoteCommand.Menu -> KeyEvent.KEYCODE_MENU
        RemoteCommand.Info -> KeyEvent.KEYCODE_INFO
        RemoteCommand.Input -> KeyEvent.KEYCODE_TV_INPUT
        RemoteCommand.Search -> KeyEvent.KEYCODE_SEARCH
        RemoteCommand.PlayPause -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        RemoteCommand.Play -> KeyEvent.KEYCODE_MEDIA_PLAY
        RemoteCommand.Pause -> KeyEvent.KEYCODE_MEDIA_PAUSE
        RemoteCommand.Stop -> KeyEvent.KEYCODE_MEDIA_STOP
        RemoteCommand.Rewind -> KeyEvent.KEYCODE_MEDIA_REWIND
        RemoteCommand.FastForward -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        RemoteCommand.Next -> KeyEvent.KEYCODE_MEDIA_NEXT
        RemoteCommand.Previous -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        RemoteCommand.Backspace -> KeyEvent.KEYCODE_DEL
        RemoteCommand.Enter -> KeyEvent.KEYCODE_ENTER
        is RemoteCommand.ColorKey -> when (command.color) {
            KeyColor.RED -> KeyEvent.KEYCODE_PROG_RED
            KeyColor.GREEN -> KeyEvent.KEYCODE_PROG_GREEN
            KeyColor.YELLOW -> KeyEvent.KEYCODE_PROG_YELLOW
            KeyColor.BLUE -> KeyEvent.KEYCODE_PROG_BLUE
        }
        else -> null
    }

    fun of(step: PointerAccumulator.Step): Int = when (step) {
        PointerAccumulator.Step.UP -> KeyEvent.KEYCODE_DPAD_UP
        PointerAccumulator.Step.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        PointerAccumulator.Step.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        PointerAccumulator.Step.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
    }

    /** Teclas para escribir [text] (el protocolo v2 no tiene "insertar texto"); omite lo que no mapea. */
    fun text(text: String): List<Int> = text.mapNotNull { ch ->
        when (ch) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (ch - 'a')
            in 'A'..'Z' -> KeyEvent.KEYCODE_A + (ch - 'A')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (ch - '0')
            ' ' -> KeyEvent.KEYCODE_SPACE
            '.' -> KeyEvent.KEYCODE_PERIOD
            ',' -> KeyEvent.KEYCODE_COMMA
            '-' -> KeyEvent.KEYCODE_MINUS
            '@' -> KeyEvent.KEYCODE_AT
            '/' -> KeyEvent.KEYCODE_SLASH
            else -> null
        }
    }
}

/** Enlaces que el TV resuelve a la app instalada (`remote_app_link_launch_request`). */
internal object AndroidTvApps {
    fun link(app: StreamingApp): String? = when (app) {
        StreamingApp.NETFLIX -> "https://www.netflix.com/title"
        StreamingApp.YOUTUBE -> "https://www.youtube.com"
        StreamingApp.PRIME_VIDEO -> "https://app.primevideo.com"
        StreamingApp.DISNEY_PLUS -> "https://www.disneyplus.com"
        StreamingApp.SPOTIFY -> "spotify://"
        StreamingApp.APPLE_TV -> "https://tv.apple.com"
        StreamingApp.BROWSER, StreamingApp.ROKU_CHANNEL, StreamingApp.SAMSUNG_TV_PLUS, StreamingApp.LG_CHANNELS -> null
    }
}
