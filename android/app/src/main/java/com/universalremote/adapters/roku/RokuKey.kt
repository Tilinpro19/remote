package com.universalremote.adapters.roku

import com.universalremote.adapters.common.PointerAccumulator
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.StreamingApp
import java.net.URLEncoder

/** Tecla ECP (`POST /keypress/{path}`). [path] ya está codificado para URL. */
@JvmInline
value class RokuKey private constructor(val path: String) {

    override fun toString(): String = path

    companion object {
        val HOME = RokuKey("Home")
        val BACK = RokuKey("Back")
        val SELECT = RokuKey("Select")
        val UP = RokuKey("Up")
        val DOWN = RokuKey("Down")
        val LEFT = RokuKey("Left")
        val RIGHT = RokuKey("Right")
        val INFO = RokuKey("Info")
        val SEARCH = RokuKey("Search")
        val PLAY = RokuKey("Play")
        val REWIND = RokuKey("Rev")
        val FAST_FORWARD = RokuKey("Fwd")
        val INSTANT_REPLAY = RokuKey("InstantReplay")
        val BACKSPACE = RokuKey("Backspace")
        val ENTER = RokuKey("Enter")
        val VOLUME_UP = RokuKey("VolumeUp")
        val VOLUME_DOWN = RokuKey("VolumeDown")
        val VOLUME_MUTE = RokuKey("VolumeMute")
        val CHANNEL_UP = RokuKey("ChannelUp")
        val CHANNEL_DOWN = RokuKey("ChannelDown")
        val POWER = RokuKey("Power")
        val POWER_ON = RokuKey("PowerOn")
        val POWER_OFF = RokuKey("PowerOff")
        val INPUT_TUNER = RokuKey("InputTuner")

        private val RAW_NAME = Regex("^[A-Za-z0-9_]+$")

        /** Tecla arbitraria (`InputHDMI1`, `FindRemote`…); rechaza caracteres que alterarían la URL. */
        fun raw(name: String): RokuKey? = name.takeIf { RAW_NAME.matches(it) }?.let(::RokuKey)

        /** Carácter literal para teclados en pantalla: `Lit_a`, `Lit_%C3%B1`. */
        fun literal(codePoint: Int): RokuKey {
            val encoded = URLEncoder.encode(String(Character.toChars(codePoint)), Charsets.UTF_8.name())
                .replace("+", "%20")
            return RokuKey("Lit_$encoded")
        }

        fun of(step: PointerAccumulator.Step): RokuKey = when (step) {
            PointerAccumulator.Step.UP -> UP
            PointerAccumulator.Step.DOWN -> DOWN
            PointerAccumulator.Step.LEFT -> LEFT
            PointerAccumulator.Step.RIGHT -> RIGHT
        }

        /** Traducción de comandos de tecla única. `null` = sin equivalente ECP. */
        fun of(command: RemoteCommand): RokuKey? = when (command) {
            RemoteCommand.Power -> POWER
            RemoteCommand.PowerOn -> POWER_ON
            RemoteCommand.PowerOff -> POWER_OFF
            RemoteCommand.VolumeUp -> VOLUME_UP
            RemoteCommand.VolumeDown -> VOLUME_DOWN
            RemoteCommand.Mute -> VOLUME_MUTE
            RemoteCommand.ChannelUp -> CHANNEL_UP
            RemoteCommand.ChannelDown -> CHANNEL_DOWN
            is RemoteCommand.Digit -> literal('0'.code + command.value)
            RemoteCommand.DpadUp -> UP
            RemoteCommand.DpadDown -> DOWN
            RemoteCommand.DpadLeft -> LEFT
            RemoteCommand.DpadRight -> RIGHT
            RemoteCommand.DpadCenter, RemoteCommand.TouchTap -> SELECT
            RemoteCommand.Back -> BACK
            RemoteCommand.Home -> HOME
            RemoteCommand.Menu, RemoteCommand.Info -> INFO
            RemoteCommand.Search -> SEARCH
            RemoteCommand.Input -> INPUT_TUNER
            RemoteCommand.PlayPause, RemoteCommand.Play, RemoteCommand.Pause -> PLAY
            RemoteCommand.Rewind -> REWIND
            RemoteCommand.FastForward, RemoteCommand.Next -> FAST_FORWARD
            RemoteCommand.Previous -> INSTANT_REPLAY
            RemoteCommand.Backspace -> BACKSPACE
            RemoteCommand.Enter -> ENTER
            is RemoteCommand.Raw -> raw(command.key)
            RemoteCommand.Stop, is RemoteCommand.TouchDelta, is RemoteCommand.Scroll, is RemoteCommand.Text,
            is RemoteCommand.ColorKey, is RemoteCommand.LaunchApp -> null
        }
    }
}

/** Identificadores de canal ECP (`POST /launch/{id}`). */
internal object RokuApps {
    fun id(app: StreamingApp): String? = when (app) {
        StreamingApp.NETFLIX -> "12"
        StreamingApp.YOUTUBE -> "837"
        StreamingApp.PRIME_VIDEO -> "13"
        StreamingApp.DISNEY_PLUS -> "291097"
        StreamingApp.SPOTIFY -> "22297"
        StreamingApp.APPLE_TV -> "551012"
        StreamingApp.ROKU_CHANNEL -> "151908"
        StreamingApp.BROWSER, StreamingApp.SAMSUNG_TV_PLUS, StreamingApp.LG_CHANNELS -> null
    }
}
