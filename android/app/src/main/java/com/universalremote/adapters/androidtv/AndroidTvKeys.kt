package com.universalremote.adapters.androidtv

import android.view.KeyEvent
import com.universalremote.domain.model.RemoteCommand

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
        RemoteCommand.DpadCenter -> KeyEvent.KEYCODE_DPAD_CENTER
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
        else -> null
    }
}
