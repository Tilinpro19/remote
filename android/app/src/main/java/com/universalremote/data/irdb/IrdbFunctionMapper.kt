package com.universalremote.data.irdb

import com.universalremote.domain.model.RemoteCommand

/**
 * Normaliza los nombres de función de IRDB/LIRC (`VOLUME +`, `KEY_VOLUMEUP`, `Cursor Up`…) al
 * `wireName` de [RemoteCommand]. Lo no reconocido se conserva como `CMD_RAW_<NOMBRE>` para que
 * la UI pueda exponerlo como tecla extra.
 */
object IrdbFunctionMapper {

    private val aliases: Map<String, RemoteCommand> = buildMap {
        fun map(command: RemoteCommand, vararg names: String) = names.forEach { put(it, command) }

        map(RemoteCommand.Power, "POWER", "POWERTOGGLE", "ONOFF", "STANDBY", "POWERONOFF")
        map(RemoteCommand.PowerOn, "POWERON", "DISCRETEON", "ON")
        map(RemoteCommand.PowerOff, "POWEROFF", "DISCRETEOFF", "OFF")
        map(RemoteCommand.VolumeUp, "VOLUME+", "VOL+", "VOLUMEUP", "VOLUP")
        map(RemoteCommand.VolumeDown, "VOLUME-", "VOL-", "VOLUMEDOWN", "VOLDOWN")
        map(RemoteCommand.Mute, "MUTE", "MUTING", "VOLUMEMUTE")
        map(RemoteCommand.ChannelUp, "CHANNEL+", "CH+", "CHANNELUP", "CHUP", "PROGRAM+", "PROG+")
        map(RemoteCommand.ChannelDown, "CHANNEL-", "CH-", "CHANNELDOWN", "CHDOWN", "PROGRAM-", "PROG-")
        map(RemoteCommand.DpadUp, "UP", "CURSORUP", "ARROWUP", "NAVUP")
        map(RemoteCommand.DpadDown, "DOWN", "CURSORDOWN", "ARROWDOWN", "NAVDOWN")
        map(RemoteCommand.DpadLeft, "LEFT", "CURSORLEFT", "ARROWLEFT", "NAVLEFT")
        map(RemoteCommand.DpadRight, "RIGHT", "CURSORRIGHT", "ARROWRIGHT", "NAVRIGHT")
        map(RemoteCommand.DpadCenter, "OK", "SELECT", "CURSORENTER", "CONFIRM", "CENTER")
        map(RemoteCommand.Enter, "ENTER")
        map(RemoteCommand.Back, "BACK", "RETURN", "EXIT")
        map(RemoteCommand.Home, "HOME", "SMARTHUB", "SMART")
        map(RemoteCommand.Menu, "MENU", "SETUP", "SETTINGS", "TOOLS")
        map(RemoteCommand.Info, "INFO", "DISPLAY", "OSD")
        map(RemoteCommand.Input, "INPUT", "SOURCE", "TV/VIDEO", "AV", "INPUTSELECT")
        map(RemoteCommand.Search, "SEARCH")
        map(RemoteCommand.PlayPause, "PLAY/PAUSE", "PLAYPAUSE")
        map(RemoteCommand.Play, "PLAY")
        map(RemoteCommand.Pause, "PAUSE")
        map(RemoteCommand.Stop, "STOP")
        map(RemoteCommand.Rewind, "REWIND", "REW", "RW")
        map(RemoteCommand.FastForward, "FASTFORWARD", "FFWD", "FF", "FORWARD", "FWD")
        map(RemoteCommand.Next, "NEXT", "SKIP+", "SKIPFORWARD", "NEXTTRACK")
        map(RemoteCommand.Previous, "PREVIOUS", "PREV", "SKIP-", "SKIPBACK", "PREVTRACK")
    }

    private val digitPattern = Regex("^(?:DIGIT|NUM|KEY)?([0-9])$")
    private val rawSanitizer = Regex("[^A-Z0-9_]")

    fun toWireName(irdbName: String): String? {
        val key = irdbName.trim().uppercase()
            .removePrefix("KEY_")
            .replace(" ", "")
            .replace("_", "")
        if (key.isEmpty()) return null
        aliases[key]?.let { return it.wireName }
        digitPattern.find(key)?.let { return RemoteCommand.Digit(it.groupValues[1].toInt()).wireName }

        val raw = irdbName.trim().uppercase().replace(rawSanitizer, "_").trim('_')
        return raw.takeIf { it.isNotEmpty() }?.let { RemoteCommand.Raw(it).wireName }
    }
}
