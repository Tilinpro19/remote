package com.universalremote

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.PairingRequest
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.presentation.FavoritesViewModel
import com.universalremote.presentation.MacrosViewModel
import com.universalremote.presentation.RemoteUiEvent
import com.universalremote.presentation.RemoteUiState
import com.universalremote.presentation.RemoteViewModel
import com.universalremote.presentation.shouldInterceptVolumeKeys
import com.universalremote.presentation.components.CyberBottomNav
import com.universalremote.presentation.components.CyberTab
import com.universalremote.presentation.components.CyberTelemetryHeader
import com.universalremote.presentation.screens.AppsAndChannelsScreen
import com.universalremote.presentation.screens.MacrosScreen
import com.universalremote.presentation.screens.DevicesScreen
import com.universalremote.presentation.screens.RemoteProScreen
import com.universalremote.presentation.screens.SettingsScreen
import com.universalremote.presentation.theme.CyberTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Lo fija la UI cuando las teclas de volumen deben ir al TV; `null` = volumen normal del teléfono. */
    var volumeKeyHandler: ((RemoteCommand) -> Unit)? = null

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val command = volumeCommandFor(keyCode)
        val handler = volumeKeyHandler
        if (command == null || handler == null) return super.onKeyDown(keyCode, event)
        // Mantener pulsado genera onKeyDown repetidos (repeatCount > 0): cada uno sube o baja un paso.
        handler(command)
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (volumeCommandFor(keyCode) != null && volumeKeyHandler != null) return true
        return super.onKeyUp(keyCode, event)
    }

    private fun volumeCommandFor(keyCode: Int): RemoteCommand? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> RemoteCommand.VolumeUp
        KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteCommand.VolumeDown
        else -> null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CyberTheme {
                RemoteAppRoot()
            }
        }
    }
}

@Composable
private fun RemoteAppRoot(vm: RemoteViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // rememberSaveable: sin esto, girar el teléfono devolvía la app a la pestaña Remoto.
    var currentTab by rememberSaveable { mutableStateOf(CyberTab.REMOTE) }
    var keyboardText by rememberSaveable { mutableStateOf<String?>(null) }

    val activity = LocalContext.current as? MainActivity
    val interceptVolume = shouldInterceptVolumeKeys(
        enabled = state.volumeKeysEnabled,
        onRemoteScreen = currentTab == CyberTab.REMOTE,
        connected = state.connection == ConnectionState.Connected,
    )
    DisposableEffect(activity, interceptVolume) {
        activity?.volumeKeyHandler = if (interceptVolume) vm::onCommandTriggered else null
        onDispose { activity?.volumeKeyHandler = null }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is RemoteUiEvent.ShowError -> snackbar.showSnackbar(event.error.message ?: "Error al enviar comando")
                is RemoteUiEvent.DeviceAdded -> snackbar.showSnackbar("Televisor añadido: ${event.profile.name}")
                is RemoteUiEvent.OpenKeyboard -> keyboardText = event.initialText
                RemoteUiEvent.CloseKeyboard -> keyboardText = null
            }
        }
    }

    val subtitle = when (currentTab) {
        CyberTab.REMOTE -> "Mando a Distancia"
        CyberTab.APPS -> "Canales & Apps"
        CyberTab.MACROS -> "Macros y Escenas"
        CyberTab.DEVICES -> "Mis Televisores"
        CyberTab.SETTINGS -> "Ajustes de la App"
    }

    Scaffold(
        topBar = {
            CyberTelemetryHeader(
                subtitle = subtitle,
                activeDevice = state.activeDevice,
                connectionState = state.connection,
                onDeviceClick = {
                    currentTab = CyberTab.DEVICES
                },
            )
        },
        bottomBar = {
            CyberBottomNav(
                selectedTab = currentTab,
                onTabSelected = { currentTab = it },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Crossfade(targetState = currentTab, label = "tabTransition") { tab ->
                when (tab) {
                    CyberTab.REMOTE -> {
                        RemoteProScreen(
                            state = state,
                            onSendCommand = { vm.onCommandTriggered(it) },
                            onNavigateToDevices = { currentTab = CyberTab.DEVICES },
                            onStartSleepTimer = vm::startSleepTimer,
                            onCancelSleepTimer = vm::cancelSleepTimer,
                            onOpenKeyboard = { keyboardText = "" },
                            isSupported = { state.activeDevice == null || vm.isSupported(it) },
                        )
                    }
                    CyberTab.APPS -> {
                        val favoritesVm: FavoritesViewModel = hiltViewModel()
                        val favorites by favoritesVm.favorites.collectAsStateWithLifecycle()
                        AppsAndChannelsScreen(
                            state = state,
                            onSendCommand = { vm.onCommandTriggered(it) },
                            onTuneChannel = vm::tuneChannel,
                            // Sin TV activo se muestra todo: al pulsar, el aviso guía a elegir uno.
                            isSupported = { state.activeDevice == null || vm.isSupported(it) },
                            favorites = favorites,
                            onToggleFavorite = favoritesVm::toggle,
                            onRemoveFavorite = favoritesVm::remove,
                        )
                    }
                    CyberTab.MACROS -> {
                        val macrosVm: MacrosViewModel = hiltViewModel()
                        val macros by macrosVm.uiState.collectAsStateWithLifecycle()
                        MacrosScreen(
                            state = macros,
                            onStartRecording = macrosVm::startRecording,
                            onCancelRecording = macrosVm::cancelRecording,
                            onSaveRecording = macrosVm::saveRecording,
                            onRun = macrosVm::run,
                            onStop = macrosVm::stop,
                            onDelete = macrosVm::delete,
                        )
                    }
                    CyberTab.DEVICES -> {
                        DevicesScreen(
                            state = state,
                            onSelectDevice = { vm.selectDevice(it) },
                            onRemoveDevice = { vm.removeDevice(it) },
                            onStartScan = { vm.startDiscovery() },
                            onStopScan = { vm.stopDiscovery() },
                            onAddDiscovered = { vm.addDiscoveredDevice(it) },
                            onAddManual = { host, kind -> vm.addManualDevice(host, kind) },
                            onOpenRemoteTab = { currentTab = CyberTab.REMOTE },
                            onReconnect = vm::reconnect,
                            onResetPairing = { vm.resetPairing() },
                        )
                    }
                    CyberTab.SETTINGS -> {
                        SettingsScreen(
                            state = state,
                            onSendCommand = { vm.onCommandTriggered(it) },
                            onHapticsChange = { vm.setHapticsEnabled(it) },
                            onVolumeKeysChange = { vm.setVolumeKeysEnabled(it) },
                        )
                    }
                }
            }
        }
    }

    // Diálogo amigable de vinculación en caso de requerir PIN
    // Se puede cerrar: si no, un TV que no muestra el aviso dejaría la app bloqueada tras el diálogo.
    var dismissedPairing by remember { mutableStateOf<PairingRequest?>(null) }
    (state.connection as? ConnectionState.AwaitingPairing)?.request?.takeIf { it != dismissedPairing }?.let { request ->
        PairingDialog(request, onSubmit = vm::submitPairingCode, onDismiss = { dismissedPairing = request })
    }

    keyboardText?.let { initial ->
        TextInputDialog(initial, onDismiss = { keyboardText = null }) {
            if (it.isNotEmpty()) vm.onCommandTriggered(RemoteCommand.Text(it))
            keyboardText = null
        }
    }
}

@Composable
private fun PairingDialog(request: PairingRequest, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    val pin = request.method as? PairingRequest.Method.Pin
    val passphrase = request.method == PairingRequest.Method.Passphrase
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Vincular con tu televisor") },
        text = {
            Column {
                Text(request.message)
                if (pin != null) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase().take(pin.length) },
                        label = { Text("Código de ${pin.length} caracteres que ves en el TV") },
                        singleLine = true,
                    )
                }
                if (passphrase) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.take(MAX_PASSPHRASE) },
                        label = { Text("Clave del TV") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            if (pin != null || passphrase) {
                TextButton(onClick = { onSubmit(code) }, enabled = if (pin != null) code.length == pin.length else code.isNotBlank()) {
                    Text("Conectar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun TextInputDialog(initial: String, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escribir en el televisor") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSend(text) }) { Text("Enviar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

private const val MAX_PASSPHRASE = 64
