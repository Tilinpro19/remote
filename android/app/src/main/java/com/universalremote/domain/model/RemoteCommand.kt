package com.universalremote.domain.model

/**
 * Evento genérico emitido por la UI. Cada [com.universalremote.domain.adapter.RemoteAdapter]
 * lo traduce a su protocolo. [wireName] es el identificador estable que usa la UI (`CMD_POWER`…).
 */
sealed class RemoteCommand(val wireName: String) {
    // Energía
    data object Power : RemoteCommand("CMD_POWER")
    data object PowerOn : RemoteCommand("CMD_POWER_ON")
    data object PowerOff : RemoteCommand("CMD_POWER_OFF")

    // Audio
    data object VolumeUp : RemoteCommand("CMD_VOL_UP")
    data object VolumeDown : RemoteCommand("CMD_VOL_DOWN")
    data object Mute : RemoteCommand("CMD_MUTE")

    // Canales
    data object ChannelUp : RemoteCommand("CMD_CH_UP")
    data object ChannelDown : RemoteCommand("CMD_CH_DOWN")
    data class Digit(val value: Int) : RemoteCommand("CMD_DIGIT_$value") {
        init {
            require(value in 0..9) { "Digit fuera de rango: $value" }
        }
    }

    // Navegación
    data object DpadUp : RemoteCommand("CMD_DPAD_UP")
    data object DpadDown : RemoteCommand("CMD_DPAD_DOWN")
    data object DpadLeft : RemoteCommand("CMD_DPAD_LEFT")
    data object DpadRight : RemoteCommand("CMD_DPAD_RIGHT")
    data object DpadCenter : RemoteCommand("CMD_DPAD_CENTER")
    data object Back : RemoteCommand("CMD_BACK")
    data object Home : RemoteCommand("CMD_HOME")
    data object Menu : RemoteCommand("CMD_MENU")
    data object Info : RemoteCommand("CMD_INFO")
    data object Input : RemoteCommand("CMD_INPUT")
    data object Search : RemoteCommand("CMD_SEARCH")

    // Multimedia
    data object PlayPause : RemoteCommand("CMD_PLAY_PAUSE")
    data object Play : RemoteCommand("CMD_PLAY")
    data object Pause : RemoteCommand("CMD_PAUSE")
    data object Stop : RemoteCommand("CMD_STOP")
    data object Rewind : RemoteCommand("CMD_REWIND")
    data object FastForward : RemoteCommand("CMD_FAST_FORWARD")
    data object Next : RemoteCommand("CMD_NEXT")
    data object Previous : RemoteCommand("CMD_PREVIOUS")

    // Puntero / touchpad (deltas en px de pantalla, +y hacia abajo)
    data class TouchDelta(val dx: Float, val dy: Float) : RemoteCommand("CMD_TOUCH_DELTA")
    data object TouchTap : RemoteCommand("CMD_TOUCH_TAP")
    data class Scroll(val dx: Float, val dy: Float) : RemoteCommand("CMD_SCROLL")

    // Texto
    data class Text(val text: String) : RemoteCommand("CMD_TEXT") {
        init {
            require(text.isNotEmpty()) { "Text vacío" }
        }
    }
    data object Backspace : RemoteCommand("CMD_BACKSPACE")
    data object Enter : RemoteCommand("CMD_ENTER")

    // Atajos
    /** Botón de color (teletexto, guía, apps HbbTV). */
    data class ColorKey(val color: KeyColor) : RemoteCommand("$COLOR_PREFIX${color.name}")

    /** Abre una app de streaming; cada adaptador la traduce a su identificador propio. */
    data class LaunchApp(val app: StreamingApp) : RemoteCommand("$APP_PREFIX${app.name}")

    /** Tecla específica del dispositivo (p. ej. `InputHDMI1` en Roku o una función IR no estándar). */
    /** Abre un vídeo de YouTube en el TV (contenido compartido desde el teléfono). */
    data class PlayYouTube(val videoId: String) : RemoteCommand("CMD_PLAY_YOUTUBE")

    data class Raw(val key: String) : RemoteCommand("CMD_RAW_$key") {
        init {
            require(key.isNotBlank()) { "Raw vacío" }
        }
    }

    val capability: RemoteCapability
        get() = when (this) {
            Power, PowerOn, PowerOff -> RemoteCapability.POWER
            VolumeUp, VolumeDown, Mute -> RemoteCapability.VOLUME
            ChannelUp, ChannelDown -> RemoteCapability.CHANNELS
            is Digit -> RemoteCapability.DIGITS
            DpadUp, DpadDown, DpadLeft, DpadRight, DpadCenter,
            Back, Home, Menu, Info, Input, Search, is Raw, is ColorKey, is LaunchApp -> RemoteCapability.NAVIGATION
            PlayPause, Play, Pause, Stop, Rewind, FastForward, Next, Previous -> RemoteCapability.MEDIA
            is TouchDelta, TouchTap, is Scroll -> RemoteCapability.POINTER
            is Text, Backspace, Enter -> RemoteCapability.KEYBOARD
            is PlayYouTube -> RemoteCapability.DEEP_LINK
        }

    val hapticWeight: HapticWeight
        get() = when (this) {
            Power, PowerOn, PowerOff -> HapticWeight.HEAVY
            Home, Back, Input, Mute, DpadCenter, TouchTap, Enter -> HapticWeight.MEDIUM
            VolumeUp, VolumeDown, ChannelUp, ChannelDown, is Digit,
            DpadUp, DpadDown, DpadLeft, DpadRight,
            PlayPause, Play, Pause, Stop, Rewind, FastForward, Next, Previous -> HapticWeight.LIGHT
            Menu, Info, Search, Backspace, is Text, is Raw, is ColorKey, is LaunchApp, is PlayYouTube -> HapticWeight.SELECTION
            is TouchDelta, is Scroll -> HapticWeight.NONE
        }

    /** Comandos de alta frecuencia que el gestor fusiona antes de enviarlos. */
    val isContinuous: Boolean
        get() = this is TouchDelta || this is Scroll

    /** Comandos que el adaptador puede usar para despertar el equipo (WoL) aunque no haya sesión. */
    val canWakeDevice: Boolean
        get() = this == Power || this == PowerOn

    companion object {
        // `lazy`: inicializarlo en el companion crearía un ciclo con los `data object` aún sin construir.
        private val parameterless: Map<String, RemoteCommand> by lazy(LazyThreadSafetyMode.PUBLICATION) {
            listOf(
                Power, PowerOn, PowerOff, VolumeUp, VolumeDown, Mute, ChannelUp, ChannelDown,
                DpadUp, DpadDown, DpadLeft, DpadRight, DpadCenter,
                Back, Home, Menu, Info, Input, Search,
                PlayPause, Play, Pause, Stop, Rewind, FastForward, Next, Previous,
                TouchTap, Backspace, Enter,
            ).associateBy { it.wireName }
        }

        private const val DIGIT_PREFIX = "CMD_DIGIT_"
        private const val RAW_PREFIX = "CMD_RAW_"
        private const val COLOR_PREFIX = "CMD_COLOR_"
        private const val APP_PREFIX = "CMD_APP_"

        /**
         * Construye un comando a partir del identificador emitido por la UI.
         * Devuelve `null` si el identificador o sus parámetros no son válidos.
         */
        fun fromWireName(wireName: String, dx: Float = 0f, dy: Float = 0f, text: String? = null): RemoteCommand? {
            parameterless[wireName]?.let { return it }
            return when {
                wireName == "CMD_TOUCH_DELTA" -> TouchDelta(dx, dy)
                wireName == "CMD_SCROLL" -> Scroll(dx, dy)
                wireName == "CMD_TEXT" -> text?.takeIf { it.isNotEmpty() }?.let(::Text)
                wireName.startsWith(DIGIT_PREFIX) ->
                    wireName.removePrefix(DIGIT_PREFIX).toIntOrNull()?.takeIf { it in 0..9 }?.let(::Digit)
                wireName.startsWith(COLOR_PREFIX) ->
                    KeyColor.entries.firstOrNull { it.name == wireName.removePrefix(COLOR_PREFIX) }?.let(::ColorKey)
                wireName.startsWith(APP_PREFIX) ->
                    StreamingApp.entries.firstOrNull { it.name == wireName.removePrefix(APP_PREFIX) }?.let(::LaunchApp)
                wireName.startsWith(RAW_PREFIX) ->
                    wireName.removePrefix(RAW_PREFIX).takeIf { it.isNotBlank() }?.let(::Raw)
                else -> null
            }
        }
    }
}

enum class KeyColor { RED, GREEN, YELLOW, BLUE }

enum class StreamingApp { NETFLIX, YOUTUBE, PRIME_VIDEO, DISNEY_PLUS, SPOTIFY, APPLE_TV, BROWSER, ROKU_CHANNEL, SAMSUNG_TV_PLUS, LG_CHANNELS }

/** Familia funcional de un comando; los adaptadores la declaran como capacidad. */
enum class RemoteCapability {
    POWER, VOLUME, CHANNELS, DIGITS, NAVIGATION, MEDIA, POINTER, KEYBOARD,

    /** Abrir contenido concreto (vídeo compartido) dentro de una app del TV. */
    DEEP_LINK,
}

/** Intensidad háptica según la criticidad del botón. */
enum class HapticWeight { NONE, SELECTION, LIGHT, MEDIUM, HEAVY }
