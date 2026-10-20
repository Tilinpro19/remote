package com.universalremote.widget

import com.universalremote.domain.model.RemoteCommand

/** Botones del widget de control rápido. */
enum class QuickAction { POWER, VOLUME_UP, VOLUME_DOWN, MUTE }

fun QuickAction.toCommand(): RemoteCommand = when (this) {
    QuickAction.POWER -> RemoteCommand.Power
    QuickAction.VOLUME_UP -> RemoteCommand.VolumeUp
    QuickAction.VOLUME_DOWN -> RemoteCommand.VolumeDown
    QuickAction.MUTE -> RemoteCommand.Mute
}
