package com.universalremote.data.local

import com.universalremote.domain.model.MacroStep
import com.universalremote.domain.model.RemoteCommand
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Serializa los pasos de una macro con el `wireName` estable de cada comando. */
object MacroStepsCodec {

    fun encode(steps: List<MacroStep>): String = JSONArray().apply {
        steps.forEach { step ->
            put(
                JSONObject()
                    .put("cmd", step.command.wireName)
                    .put("delay", step.delayAfterMs)
                    .apply { (step.command as? RemoteCommand.Text)?.let { put("text", it.text) } },
            )
        }
    }.toString()

    /** Pasos desconocidos (de una versión futura) o JSON corrupto se descartan en lugar de fallar. */
    fun decode(json: String): List<MacroStep> {
        val array = try {
            JSONArray(json)
        } catch (e: JSONException) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val command = RemoteCommand.fromWireName(obj.optString("cmd"), text = obj.optString("text"))
                ?: return@mapNotNull null
            MacroStep(command, obj.optLong("delay", MacroStep(command).delayAfterMs))
        }
    }
}
