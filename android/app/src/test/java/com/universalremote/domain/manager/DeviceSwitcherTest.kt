package com.universalremote.domain.manager

import com.universalremote.domain.adapter.AdapterEvent
import com.universalremote.domain.adapter.RemoteAdapter
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.RemoteCapability
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.repository.DeviceRepository
import com.universalremote.domain.repository.SessionPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** El selector del encabezado delega en [RemoteControllerManager.selectDevice]: aquí se fija su efecto. */
class DeviceSwitcherTest {

    private val salon = DeviceProfile(id = "salon", name = "Salón", kind = DeviceKind.ROKU, transport = DeviceTransport.Lan("10.0.0.2", 8060))
    private val cuarto = DeviceProfile(id = "cuarto", name = "Cuarto", kind = DeviceKind.ROKU, transport = DeviceTransport.Lan("10.0.0.3", 8060))
    private val adapters = mutableMapOf<String, FakeAdapter>()

    @Test
    fun `cambiar de dispositivo activa el nuevo y cierra el anterior`() = runTest {
        val manager = manager()
        manager.selectDevice("salon")
        advanceUntilIdle()

        manager.selectDevice("cuarto")
        advanceUntilIdle()

        assertEquals(cuarto, manager.activeDevice.value)
        assertTrue(adapters.getValue("salon").closed)
        assertFalse(adapters.getValue("cuarto").closed)
        assertEquals("cuarto", prefs.activeDeviceId.first())
    }

    private val prefs = FakePreferences()

    private fun TestScope.manager() = RemoteControllerManager(
        adapterFactory = { profile -> FakeAdapter(profile).also { adapters[profile.id] = it } },
        deviceRepository = FakeDevices(listOf(salon, cuarto)),
        preferences = prefs,
        scope = backgroundScope,
    )

    private class FakeAdapter(override val profile: DeviceProfile) : RemoteAdapter {
        var closed = false
        override val state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
        override val events = MutableSharedFlow<AdapterEvent>()
        override val capabilities = setOf(RemoteCapability.VOLUME)
        override suspend fun connect() {
            state.value = ConnectionState.Connected
        }
        override suspend fun disconnect() {
            state.value = ConnectionState.Idle
        }
        override suspend fun send(command: RemoteCommand) = Unit
        override suspend fun close() {
            closed = true
        }
    }

    private class FakeDevices(initial: List<DeviceProfile>) : DeviceRepository {
        private val devices = MutableStateFlow(initial.associateBy { it.id })
        override fun observeDevices(): Flow<List<DeviceProfile>> = devices.map { it.values.toList() }
        override suspend fun get(id: String) = devices.value[id]
        override suspend fun upsert(profile: DeviceProfile) {
            devices.value += profile.id to profile
        }
        override suspend fun delete(id: String) {
            devices.value -= id
        }
    }

    private class FakePreferences : SessionPreferences {
        override val activeDeviceId = MutableStateFlow<String?>(null)
        override suspend fun setActiveDeviceId(id: String?) {
            activeDeviceId.value = id
        }
        override val hapticsEnabled = MutableStateFlow(true)
        override suspend fun setHapticsEnabled(enabled: Boolean) = Unit
        override val volumeKeysEnabled = MutableStateFlow(false)
        override suspend fun setVolumeKeysEnabled(enabled: Boolean) = Unit
        override val autoWakeEnabled = MutableStateFlow(false)
        override suspend fun setAutoWakeEnabled(enabled: Boolean) = Unit
    }
}
