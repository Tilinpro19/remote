package com.universalremote.adapters.philips

import com.universalremote.domain.model.KeyColor
import com.universalremote.domain.model.RemoteCommand

/** Nombres de tecla de la API JointSpace v6 (`POST /6/input/key`). */
object PhilipsKeys {

    fun nameFor(command: RemoteCommand): String? = when (command) {
        RemoteCommand.Power -> "Standby"
        RemoteCommand.VolumeUp -> "VolumeUp"
        RemoteCommand.VolumeDown -> "VolumeDown"
        RemoteCommand.Mute -> "Mute"
        RemoteCommand.ChannelUp -> "ChannelStepUp"
        RemoteCommand.ChannelDown -> "ChannelStepDown"
        is RemoteCommand.Digit -> "Digit${command.value}"
        RemoteCommand.DpadUp -> "CursorUp"
        RemoteCommand.DpadDown -> "CursorDown"
        RemoteCommand.DpadLeft -> "CursorLeft"
        RemoteCommand.DpadRight -> "CursorRight"
        RemoteCommand.DpadCenter -> "Confirm"
        RemoteCommand.Back -> "Back"
        RemoteCommand.Home -> "Home"
        RemoteCommand.Menu -> "Options"
        RemoteCommand.Info -> "Info"
        RemoteCommand.Input -> "Source"
        RemoteCommand.Search -> "Find"
        RemoteCommand.PlayPause -> "PlayPause"
        RemoteCommand.Play -> "Play"
        RemoteCommand.Pause -> "Pause"
        RemoteCommand.Stop -> "Stop"
        RemoteCommand.Rewind -> "Rewind"
        RemoteCommand.FastForward -> "FastForward"
        RemoteCommand.Next -> "Next"
        RemoteCommand.Previous -> "Previous"
        is RemoteCommand.ColorKey -> when (command.color) {
            KeyColor.RED -> "RedColour"
            KeyColor.GREEN -> "GreenColour"
            KeyColor.YELLOW -> "YellowColour"
            KeyColor.BLUE -> "BlueColour"
        }
        else -> null
    }
}
