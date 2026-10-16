package com.universalremote.presentation

import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DiscoveredDevice
import com.universalremote.domain.model.IrCodeset
import com.universalremote.domain.model.IrDeviceCategory
import com.universalremote.domain.model.RemoteCapability
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.RemoteError
import com.universalremote.domain.repository.BrandPopularity

/** Estado único que consume la UI existente. */
data class RemoteUiState(
    val devices: List<DeviceProfile> = emptyList(),
    val activeDevice: DeviceProfile? = null,
    val connection: ConnectionState = ConnectionState.Idle,
    /** Para habilitar/ocultar bloques de botones (volumen, canales, touchpad…). */
    val capabilities: Set<RemoteCapability> = emptySet(),
    val discovery: DiscoveryUiState = DiscoveryUiState(),
    val irSetup: IrSetupState = IrSetupState.Idle,
    val hasIrEmitter: Boolean = false,
    val hapticsEnabled: Boolean = true,
    /** Minutos programados del temporizador de apagado; `null` si no hay ninguno activo. */
    val sleepTimerMinutes: Int? = null,
)

data class DiscoveryUiState(
    val isScanning: Boolean = false,
    val devices: List<DiscoveredDevice> = emptyList(),
)

/** Flujo de prueba por descarte de codesets IR. */
sealed interface IrSetupState {
    data object Idle : IrSetupState
    data object Loading : IrSetupState
    data class BrandSelection(val category: IrDeviceCategory, val brands: List<BrandPopularity>) : IrSetupState
    data class Testing(
        val brand: String,
        val category: IrDeviceCategory,
        val candidate: IrCodeset,
        /** Índice 0-based del candidato actual. */
        val index: Int,
        val total: Int,
        val bridgeUrl: String?,
        val isSending: Boolean = false,
    ) : IrSetupState

    /** Se probaron todos los codesets sin éxito. */
    data class Exhausted(val brand: String, val category: IrDeviceCategory) : IrSetupState
}

/** Eventos de un solo disparo (snackbars, teclado, navegación). */
sealed interface RemoteUiEvent {
    data class ShowError(val error: RemoteError, val command: RemoteCommand? = null) : RemoteUiEvent
    data class OpenKeyboard(val initialText: String) : RemoteUiEvent
    data object CloseKeyboard : RemoteUiEvent
    data class DeviceAdded(val profile: DeviceProfile) : RemoteUiEvent
}
