package com.universalremote.adapters.panasonic

import com.universalremote.domain.model.KeyColor
import com.universalremote.domain.model.RemoteCommand

/** Códigos `NRC_*-ONOFF` de la acción `X_SendKey` de Panasonic Viera. */
object VieraKeys {

    fun keyFor(command: RemoteCommand): String? = when (command) {
        RemoteCommand.Power, RemoteCommand.PowerOff -> "NRC_POWER-ONOFF"
        RemoteCommand.VolumeUp -> "NRC_VOLUP-ONOFF"
        RemoteCommand.VolumeDown -> "NRC_VOLDOWN-ONOFF"
        RemoteCommand.Mute -> "NRC_MUTE-ONOFF"
        RemoteCommand.ChannelUp -> "NRC_CH_UP-ONOFF"
        RemoteCommand.ChannelDown -> "NRC_CH_DOWN-ONOFF"
        is RemoteCommand.Digit -> "NRC_D${command.value}-ONOFF"
        RemoteCommand.DpadUp -> "NRC_UP-ONOFF"
        RemoteCommand.DpadDown -> "NRC_DOWN-ONOFF"
        RemoteCommand.DpadLeft -> "NRC_LEFT-ONOFF"
        RemoteCommand.DpadRight -> "NRC_RIGHT-ONOFF"
        RemoteCommand.DpadCenter -> "NRC_ENTER-ONOFF"
        RemoteCommand.Back -> "NRC_RETURN-ONOFF"
        RemoteCommand.Home -> "NRC_HOME-ONOFF"
        RemoteCommand.Menu -> "NRC_MENU-ONOFF"
        RemoteCommand.Info -> "NRC_INFO-ONOFF"
        RemoteCommand.Input -> "NRC_CHG_INPUT-ONOFF"
        RemoteCommand.Play -> "NRC_PLAY-ONOFF"
        RemoteCommand.Pause -> "NRC_PAUSE-ONOFF"
        RemoteCommand.Stop -> "NRC_STOP-ONOFF"
        RemoteCommand.Rewind -> "NRC_REW-ONOFF"
        RemoteCommand.FastForward -> "NRC_FF-ONOFF"
        RemoteCommand.Next -> "NRC_SKIP_NEXT-ONOFF"
        RemoteCommand.Previous -> "NRC_SKIP_PREV-ONOFF"
        is RemoteCommand.ColorKey -> when (command.color) {
            KeyColor.RED -> "NRC_RED-ONOFF"
            KeyColor.GREEN -> "NRC_GREEN-ONOFF"
            KeyColor.YELLOW -> "NRC_YELLOW-ONOFF"
            KeyColor.BLUE -> "NRC_BLUE-ONOFF"
        }
        else -> null
    }
}
