package com.universalremote.domain.model

/** Paso de una macro: el comando y la pausa antes del siguiente (el TV necesita tiempo para reaccionar). */
data class MacroStep(val command: RemoteCommand, val delayAfterMs: Long = 300)

/** Secuencia de comandos con nombre, ligada a un dispositivo guardado. */
data class Macro(val id: String, val name: String, val deviceId: String, val steps: List<MacroStep>)
