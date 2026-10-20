package com.universalremote.domain.manager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoWakePolicyTest {

    @Test
    fun `despierta solo con el ajuste activo, MAC conocida y TV apagado`() {
        assertTrue(shouldAutoWake(autoWakeEnabled = true, hasMac = true, powered = false))
    }

    @Test
    fun `no despierta si falta alguna condicion`() {
        assertFalse(shouldAutoWake(autoWakeEnabled = false, hasMac = true, powered = false))
        assertFalse(shouldAutoWake(autoWakeEnabled = true, hasMac = false, powered = false))
        assertFalse(shouldAutoWake(autoWakeEnabled = true, hasMac = true, powered = true))
    }

    @Test
    fun `con estado desconocido no envia nada`() {
        assertFalse(shouldAutoWake(autoWakeEnabled = true, hasMac = true, powered = null))
    }
}
