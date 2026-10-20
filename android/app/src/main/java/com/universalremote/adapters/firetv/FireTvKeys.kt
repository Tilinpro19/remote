package com.universalremote.adapters.firetv

import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.StreamingApp

/** `KEYCODE_*` de Android para `input keyevent` y paquetes de apps en Fire OS. */
object FireTvKeys {

    fun keyEventFor(command: RemoteCommand): Int? = when (command) {
        RemoteCommand.Power -> 26
        RemoteCommand.PowerOn -> 224 // KEYCODE_WAKEUP
        RemoteCommand.PowerOff -> 223 // KEYCODE_SLEEP
        RemoteCommand.VolumeUp -> 24
        RemoteCommand.VolumeDown -> 25
        RemoteCommand.Mute -> 164
        is RemoteCommand.Digit -> 7 + command.value
        RemoteCommand.DpadUp -> 19
        RemoteCommand.DpadDown -> 20
        RemoteCommand.DpadLeft -> 21
        RemoteCommand.DpadRight -> 22
        RemoteCommand.DpadCenter -> 23
        RemoteCommand.Back -> 4
        RemoteCommand.Home -> 3
        RemoteCommand.Menu -> 82
        RemoteCommand.Search -> 84
        RemoteCommand.PlayPause -> 85
        RemoteCommand.Play -> 126
        RemoteCommand.Pause -> 127
        RemoteCommand.Stop -> 86
        RemoteCommand.Rewind -> 89
        RemoteCommand.FastForward -> 90
        RemoteCommand.Next -> 87
        RemoteCommand.Previous -> 88
        RemoteCommand.Backspace -> 67
        RemoteCommand.Enter -> 66
        else -> null
    }

    /**
     * `input text` interpreta `%s` como espacio. Se envuelve en comillas simples para que la
     * shell remota no expanda `$`, `;` ni `&` del texto del usuario.
     */
    fun inputTextCommand(text: String): String =
        "input text '" + text.replace(" ", "%s").replace("'", "'\\''") + "'"

    /** Paquetes de Fire OS. Pendiente de verificar en un dispositivo real. */
    fun packageFor(app: StreamingApp): String? = when (app) {
        StreamingApp.NETFLIX -> "com.netflix.ninja"
        StreamingApp.PRIME_VIDEO -> "com.amazon.firebat"
        StreamingApp.YOUTUBE -> "com.amazon.firetv.youtube"
        StreamingApp.DISNEY_PLUS -> "com.disney.disneyplus"
        StreamingApp.SPOTIFY -> "com.spotify.tv.android"
        else -> null
    }
}
