package com.universalremote.adapters.yamaha

import com.universalremote.domain.model.RemoteCommand

/**
 * Rutas de Yamaha Extended Control (zona `main` y reproductor `netusb`) por comando.
 * El silencio no está aquí: necesita leer el estado antes (ver [YamahaAdapter.send]).
 */
object YamahaKeys {

    fun pathFor(command: RemoteCommand): String? = when (command) {
        RemoteCommand.Power -> "main/setPower?power=toggle"
        RemoteCommand.PowerOn -> "main/setPower?power=on"
        RemoteCommand.PowerOff -> "main/setPower?power=standby"
        RemoteCommand.VolumeUp -> "main/setVolume?volume=up"
        RemoteCommand.VolumeDown -> "main/setVolume?volume=down"
        RemoteCommand.DpadUp -> cursor("up")
        RemoteCommand.DpadDown -> cursor("down")
        RemoteCommand.DpadLeft -> cursor("left")
        RemoteCommand.DpadRight -> cursor("right")
        RemoteCommand.DpadCenter -> cursor("select")
        RemoteCommand.Back -> cursor("return")
        RemoteCommand.Menu -> "main/controlMenu?menu=on_screen"
        RemoteCommand.PlayPause -> playback("play_pause")
        RemoteCommand.Play -> playback("play")
        RemoteCommand.Pause -> playback("pause")
        RemoteCommand.Stop -> playback("stop")
        RemoteCommand.Next -> playback("next")
        RemoteCommand.Previous -> playback("previous")
        else -> null
    }

    private fun cursor(value: String) = "main/controlCursor?cursor=$value"

    private fun playback(value: String) = "netusb/setPlayback?playback=$value"
}
