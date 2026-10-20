package com.universalremote.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeKeyPolicyTest {

    @Test
    fun `solo intercepta con ajuste activo, mando visible y equipo conectado`() {
        assertTrue(shouldInterceptVolumeKeys(enabled = true, onRemoteScreen = true, connected = true))
    }

    @Test
    fun `en cualquier otro caso deja el volumen del telefono`() {
        for (enabled in listOf(true, false)) for (onRemote in listOf(true, false)) for (connected in listOf(true, false)) {
            if (enabled && onRemote && connected) continue
            assertFalse(shouldInterceptVolumeKeys(enabled, onRemote, connected))
        }
    }
}
