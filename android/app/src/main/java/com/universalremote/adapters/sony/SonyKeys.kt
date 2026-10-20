package com.universalremote.adapters.sony

import com.universalremote.domain.model.KeyColor
import com.universalremote.domain.model.RemoteCommand

/**
 * Códigos IRCC (base64) de los TV Sony Bravia, tomados de la tabla pública que devuelve
 * `getRemoteControllerInfo`. Son estables entre modelos 2013+.
 * Pendiente de verificar en un TV real: Menu (Options) y los botones de color.
 */
object SonyKeys {

    fun codeFor(command: RemoteCommand): String? = when (command) {
        RemoteCommand.Power -> "AAAAAQAAAAEAAAAVAw=="
        RemoteCommand.VolumeUp -> "AAAAAQAAAAEAAAASAw=="
        RemoteCommand.VolumeDown -> "AAAAAQAAAAEAAAATAw=="
        RemoteCommand.Mute -> "AAAAAQAAAAEAAAAUAw=="
        RemoteCommand.ChannelUp -> "AAAAAQAAAAEAAAAQAw=="
        RemoteCommand.ChannelDown -> "AAAAAQAAAAEAAAARAw=="
        is RemoteCommand.Digit -> DIGITS[command.value]
        RemoteCommand.DpadUp -> "AAAAAQAAAAEAAAB0Aw=="
        RemoteCommand.DpadDown -> "AAAAAQAAAAEAAAB1Aw=="
        RemoteCommand.DpadLeft -> "AAAAAQAAAAEAAAA0Aw=="
        RemoteCommand.DpadRight -> "AAAAAQAAAAEAAAAzAw=="
        RemoteCommand.DpadCenter -> "AAAAAQAAAAEAAABlAw=="
        RemoteCommand.Back -> "AAAAAgAAAJcAAAAjAw=="
        RemoteCommand.Home -> "AAAAAQAAAAEAAABgAw=="
        RemoteCommand.Menu -> "AAAAAgAAAJcAAAA2Aw=="
        RemoteCommand.Info -> "AAAAAQAAAAEAAAA6Aw=="
        RemoteCommand.Input -> "AAAAAQAAAAEAAAAlAw=="
        RemoteCommand.Play -> "AAAAAgAAAJcAAAAaAw=="
        RemoteCommand.Pause -> "AAAAAgAAAJcAAAAZAw=="
        RemoteCommand.Stop -> "AAAAAgAAAJcAAAAYAw=="
        RemoteCommand.Rewind -> "AAAAAgAAAJcAAAAbAw=="
        RemoteCommand.FastForward -> "AAAAAgAAAJcAAAAcAw=="
        RemoteCommand.Next -> "AAAAAgAAAJcAAAA9Aw=="
        RemoteCommand.Previous -> "AAAAAgAAAJcAAAA8Aw=="
        is RemoteCommand.ColorKey -> when (command.color) {
            KeyColor.RED -> "AAAAAgAAAJcAAAAlAw=="
            KeyColor.GREEN -> "AAAAAgAAAJcAAAAmAw=="
            KeyColor.YELLOW -> "AAAAAgAAAJcAAAAnAw=="
            KeyColor.BLUE -> "AAAAAgAAAJcAAAAkAw=="
        }
        else -> null
    }

    // Num0 es 0x09; Num1..Num9 son 0x00..0x08.
    private val DIGITS = listOf(
        "AAAAAQAAAAEAAAAJAw==", "AAAAAQAAAAEAAAAAAw==", "AAAAAQAAAAEAAAABAw==", "AAAAAQAAAAEAAAACAw==",
        "AAAAAQAAAAEAAAADAw==", "AAAAAQAAAAEAAAAEAw==", "AAAAAQAAAAEAAAAFAw==", "AAAAAQAAAAEAAAAGAw==",
        "AAAAAQAAAAEAAAAHAw==", "AAAAAQAAAAEAAAAIAw==",
    )
}
