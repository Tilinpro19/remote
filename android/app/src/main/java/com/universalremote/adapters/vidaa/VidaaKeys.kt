package com.universalremote.adapters.vidaa

import com.universalremote.domain.model.RemoteCommand
import org.json.JSONException
import org.json.JSONObject

/** Nombres de tecla que acepta el servicio remoto de VIDAA (payload de `.../actions/sendkey`). */
object VidaaKeys {

    fun keyFor(command: RemoteCommand): String? = when (command) {
        RemoteCommand.Power, RemoteCommand.PowerOff -> "KEY_POWER"
        RemoteCommand.VolumeUp -> "KEY_VOLUMEUP"
        RemoteCommand.VolumeDown -> "KEY_VOLUMEDOWN"
        RemoteCommand.Mute -> "KEY_MUTE"
        RemoteCommand.DpadUp -> "KEY_UP"
        RemoteCommand.DpadDown -> "KEY_DOWN"
        RemoteCommand.DpadLeft -> "KEY_LEFT"
        RemoteCommand.DpadRight -> "KEY_RIGHT"
        RemoteCommand.DpadCenter -> "KEY_OK"
        RemoteCommand.Back -> "KEY_RETURNS"
        RemoteCommand.Home -> "KEY_HOME"
        RemoteCommand.Menu -> "KEY_MENU"
        RemoteCommand.Play -> "KEY_PLAY"
        RemoteCommand.Pause -> "KEY_PAUSE"
        RemoteCommand.Stop -> "KEY_STOP"
        RemoteCommand.Rewind -> "KEY_BACKS"
        RemoteCommand.FastForward -> "KEY_FORWARDS"
        else -> null
    }
}

/** Tópicos MQTT del TV; [clientId] identifica al móvil y debe ser estable entre sesiones. */
class VidaaTopics(clientId: String) {
    private val id = "$clientId\$normal"
    val sendKey = "/remoteapp/tv/remote_service/$id/actions/sendkey"
    val authCode = "/remoteapp/tv/ui_service/$id/actions/authenticationcode"
    val authResult = "/remoteapp/mobile/$id/ui_service/data/authenticationcode"

    /** Pide el estado del TV; si el móvil no está autorizado, el TV muestra el código de 4 dígitos. */
    val getTvState = "/remoteapp/tv/ui_service/$id/actions/gettvstate"
    val tvState = "/remoteapp/mobile/$id/ui_service/data/gettvstate"

    companion object {
        fun authPayload(code: String): String = JSONObject().put("authNum", code.trim().toInt()).toString()

        fun isAuthAccepted(payload: String): Boolean = try {
            JSONObject(payload).optInt("result") == 1
        } catch (e: JSONException) {
            false
        }
    }
}
