package com.universalremote.adapters.kodi

import com.universalremote.domain.model.RemoteCommand
import org.json.JSONObject

/** Traducción de [RemoteCommand] a métodos JSON-RPC de Kodi. */
object KodiKeys {

    data class Call(val method: String, val params: JSONObject? = null)

    fun callFor(command: RemoteCommand): Call? = when (command) {
        RemoteCommand.Power, RemoteCommand.PowerOff -> Call("System.Shutdown")
        // Con sesión activa Kodi ya está encendido; apagado, el adaptador recurre a Wake-on-LAN.
        RemoteCommand.PowerOn -> Call("JSONRPC.Ping")
        RemoteCommand.VolumeUp -> Call("Application.SetVolume", JSONObject().put("volume", "increment"))
        RemoteCommand.VolumeDown -> Call("Application.SetVolume", JSONObject().put("volume", "decrement"))
        RemoteCommand.Mute -> Call("Application.SetMute", JSONObject().put("mute", "toggle"))
        RemoteCommand.DpadUp -> Call("Input.Up")
        RemoteCommand.DpadDown -> Call("Input.Down")
        RemoteCommand.DpadLeft -> Call("Input.Left")
        RemoteCommand.DpadRight -> Call("Input.Right")
        RemoteCommand.DpadCenter -> Call("Input.Select")
        RemoteCommand.Back -> Call("Input.Back")
        RemoteCommand.Home -> Call("Input.Home")
        RemoteCommand.Menu -> Call("Input.ContextMenu")
        RemoteCommand.Info -> Call("Input.Info")
        // Input.ExecuteAction actúa sobre el reproductor activo sin tener que pedir su playerid.
        RemoteCommand.PlayPause -> action("playpause")
        RemoteCommand.Play -> action("play")
        RemoteCommand.Pause -> action("pause")
        RemoteCommand.Stop -> action("stop")
        RemoteCommand.Rewind -> action("rewind")
        RemoteCommand.FastForward -> action("fastforward")
        RemoteCommand.Next -> action("skipnext")
        RemoteCommand.Previous -> action("skipprevious")
        else -> null
    }

    private fun action(name: String) = Call("Input.ExecuteAction", JSONObject().put("action", name))
}
