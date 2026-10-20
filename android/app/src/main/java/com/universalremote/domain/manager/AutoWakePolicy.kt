package com.universalremote.domain.manager

/**
 * Despertar al abrir la app sólo si el usuario lo pidió, hay MAC para Wake-on-LAN y el equipo
 * informó que está apagado. Con estado desconocido (`null`) no se envía nada: mejor no encender
 * un TV que quizá ya está encendido o que la marca no sabe consultar.
 */
fun shouldAutoWake(autoWakeEnabled: Boolean, hasMac: Boolean, powered: Boolean?): Boolean =
    autoWakeEnabled && hasMac && powered == false
