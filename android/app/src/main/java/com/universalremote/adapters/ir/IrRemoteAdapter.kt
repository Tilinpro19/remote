package com.universalremote.adapters.ir

import com.universalremote.adapters.common.PointerAccumulator
import com.universalremote.domain.adapter.AdapterEvent
import com.universalremote.domain.adapter.RemoteAdapter
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DeviceTransport
import com.universalremote.domain.model.IrCode
import com.universalremote.domain.model.IrProtocol
import com.universalremote.domain.model.RemoteCapability
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import com.universalremote.domain.repository.IrCodeRepository
import com.universalremote.hardware.ir.HttpIrBridgeTransmitter
import com.universalremote.hardware.ir.IrHardwareManager
import com.universalremote.hardware.ir.IrProtocolEncoder
import com.universalremote.hardware.ir.IrTransmitter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Adaptador de infrarrojos. "Conectar" significa validar el emisor (local o hub IoT) y cargar las
 * funciones del codeset; no hay sesión real porque IR es unidireccional.
 */
class IrRemoteAdapter @AssistedInject constructor(
    @Assisted override val profile: DeviceProfile,
    private val codes: IrCodeRepository,
    private val localEmitter: IrHardwareManager,
    private val bridgeFactory: HttpIrBridgeTransmitter.Factory,
) : RemoteAdapter {

    private val transport: DeviceTransport.Infrared = profile.transport as? DeviceTransport.Infrared
        ?: throw IllegalArgumentException("IrRemoteAdapter requiere DeviceTransport.Infrared")

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    /** IR es unidireccional: nunca aprende datos ni pide teclado. */
    override val events: SharedFlow<AdapterEvent> = MutableSharedFlow<AdapterEvent>().asSharedFlow()

    private val mutex = Mutex()
    private val pointer = PointerAccumulator()
    private val codeCache = HashMap<String, IrCode>()

    @Volatile
    private var functions: Set<String> = emptySet()

    /** Bit de alternancia RC5/RC6: el receptor lo usa para distinguir pulsación nueva de repetición. */
    private var toggle = false

    private var transmitter: IrTransmitter? = null

    override val capabilities: Set<RemoteCapability>
        get() = buildSet {
            functions.forEach { name -> RemoteCommand.fromWireName(name)?.let { add(it.capability) } }
            if (DPAD_FUNCTIONS.all { it in functions }) add(RemoteCapability.POINTER)
        }

    override fun supports(command: RemoteCommand): Boolean = when (command) {
        is RemoteCommand.Text -> false
        is RemoteCommand.TouchDelta -> DPAD_FUNCTIONS.all { it in functions }
        is RemoteCommand.Scroll -> UP_DOWN_FUNCTIONS.all { it in functions }
        else -> isMapped(command)
    }

    override suspend fun connect() = mutex.withLock { connectLocked() }

    override suspend fun disconnect() = mutex.withLock {
        pointer.reset()
        _state.value = ConnectionState.Idle
    }

    override suspend fun send(command: RemoteCommand) = mutex.withLock {
        if (_state.value != ConnectionState.Connected) connectLocked()
        when (command) {
            is RemoteCommand.TouchDelta -> pointer.accumulate(command.dx, command.dy).forEach { transmitFunction(it.function) }
            is RemoteCommand.Scroll -> pointer.accumulate(0f, command.dy).forEach { transmitFunction(it.function) }
            else -> {
                val function = candidates(command).firstOrNull { it in functions }
                    ?: throw RemoteError.UnsupportedCommand(command.wireName)
                transmitFunction(function)
            }
        }
    }

    private suspend fun connectLocked() {
        if (_state.value == ConnectionState.Connected) return
        _state.value = ConnectionState.Connecting
        try {
            val tx = transmitter ?: resolveTransmitter().also { transmitter = it }
            if (!tx.isAvailable) throw RemoteError.HardwareUnavailable("Emisor IR no disponible")
            if (functions.isEmpty()) {
                functions = codes.functions(transport.codesetId)
                if (functions.isEmpty()) throw RemoteError.InvalidCode("El codeset ${transport.codesetId} no tiene códigos")
            }
            _state.value = ConnectionState.Connected
        } catch (t: Throwable) {
            val error = RemoteError.from(t)
            _state.value = ConnectionState.Failed(error)
            throw error
        }
    }

    private fun resolveTransmitter(): IrTransmitter {
        transport.bridgeUrl?.let { return bridgeFactory.create(it) }
        if (localEmitter.hasIrEmitter()) return localEmitter
        throw RemoteError.HardwareUnavailable(
            "Este teléfono no tiene emisor IR; configura un hub IR en la red local para este equipo",
        )
    }

    private suspend fun transmitFunction(function: String) {
        val code = codeCache[function]
            ?: codes.code(transport.codesetId, function)?.also { codeCache[function] = it }
            ?: throw RemoteError.UnsupportedCommand(function)
        val usesToggle = code.protocol == IrProtocol.RC5 || code.protocol == IrProtocol.RC6
        val signal = IrProtocolEncoder.encode(code, toggle)
        val tx = transmitter ?: throw RemoteError.NotConnected()
        try {
            tx.transmit(signal)
        } catch (t: Throwable) {
            val error = RemoteError.from(t)
            if (error is RemoteError.HardwareUnavailable || error is RemoteError.PermissionDenied || error is RemoteError.Unreachable) {
                _state.value = ConnectionState.Failed(error)
            }
            throw error
        }
        if (usesToggle) toggle = !toggle
    }

    private fun isMapped(command: RemoteCommand): Boolean = candidates(command).any { it in functions }

    private val PointerAccumulator.Step.function: String
        get() = when (this) {
            PointerAccumulator.Step.UP -> RemoteCommand.DpadUp.wireName
            PointerAccumulator.Step.DOWN -> RemoteCommand.DpadDown.wireName
            PointerAccumulator.Step.LEFT -> RemoteCommand.DpadLeft.wireName
            PointerAccumulator.Step.RIGHT -> RemoteCommand.DpadRight.wireName
        }

    @AssistedFactory
    interface Factory {
        fun create(profile: DeviceProfile): IrRemoteAdapter
    }

    private companion object {
        val DPAD_FUNCTIONS = listOf(RemoteCommand.DpadUp, RemoteCommand.DpadDown, RemoteCommand.DpadLeft, RemoteCommand.DpadRight).map { it.wireName }
        val UP_DOWN_FUNCTIONS = listOf(RemoteCommand.DpadUp, RemoteCommand.DpadDown).map { it.wireName }

        /** Funciones del catálogo a probar en orden para cada comando (equivalencias razonables). */
        fun candidates(command: RemoteCommand): List<String> = when (command) {
            RemoteCommand.Power -> listOf(command.wireName)
            RemoteCommand.PowerOn -> listOf(command.wireName, RemoteCommand.Power.wireName)
            RemoteCommand.PowerOff -> listOf(command.wireName, RemoteCommand.Power.wireName)
            RemoteCommand.PlayPause -> listOf(command.wireName, RemoteCommand.Play.wireName)
            RemoteCommand.Pause -> listOf(command.wireName, RemoteCommand.PlayPause.wireName)
            RemoteCommand.Play -> listOf(command.wireName, RemoteCommand.PlayPause.wireName)
            RemoteCommand.DpadCenter -> listOf(command.wireName, RemoteCommand.Enter.wireName)
            RemoteCommand.TouchTap -> listOf(RemoteCommand.DpadCenter.wireName, RemoteCommand.Enter.wireName)
            RemoteCommand.Enter -> listOf(command.wireName, RemoteCommand.DpadCenter.wireName)
            RemoteCommand.Home -> listOf(command.wireName, RemoteCommand.Menu.wireName)
            is RemoteCommand.ColorKey -> listOf(command.wireName, RemoteCommand.Raw(command.color.name).wireName)
            else -> listOf(command.wireName)
        }
    }
}
