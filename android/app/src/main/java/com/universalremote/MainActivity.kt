package com.universalremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.RemoteCapability
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.presentation.RemoteUiEvent
import com.universalremote.presentation.RemoteUiState
import com.universalremote.presentation.RemoteViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { RemoteScreen() }
        }
    }
}

@Composable
private fun RemoteScreen(vm: RemoteViewModel = hiltViewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var keyboardText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is RemoteUiEvent.ShowError -> snackbar.showSnackbar(event.error.message ?: "Error")
                is RemoteUiEvent.DeviceAdded -> snackbar.showSnackbar("Añadido: ${event.profile.name}")
                is RemoteUiEvent.OpenKeyboard -> keyboardText = event.initialText
                RemoteUiEvent.CloseKeyboard -> keyboardText = null
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Universal Remote", style = MaterialTheme.typography.headlineSmall)
            DevicesSection(state, vm)
            DiscoverySection(state, vm)
            if (state.activeDevice != null) RemotePad(state) { vm.onCommandTriggered(it) }
        }
    }

    (state.connection as? ConnectionState.AwaitingPairing)?.let { pairing ->
        PairingDialog(pairing.request.message, onSubmit = vm::submitPairingCode)
    }
    keyboardText?.let { initial ->
        TextInputDialog(initial, onDismiss = { keyboardText = null }) {
            if (it.isNotEmpty()) vm.onCommandTriggered(RemoteCommand.Text(it))
            keyboardText = null
        }
    }
}

@Composable
private fun DevicesSection(state: RemoteUiState, vm: RemoteViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mis dispositivos", style = MaterialTheme.typography.titleMedium)
            if (state.devices.isEmpty()) Text("Ninguno todavía. Busca equipos en tu red.")
            state.devices.forEach { device ->
                val active = device.id == state.activeDevice?.id
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (active) "● " else "") + device.name,
                        Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.selectDevice(device.id) }) { Text("Usar") }
                    TextButton(onClick = { vm.removeDevice(device.id) }) { Text("Quitar") }
                }
            }
            state.activeDevice?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Estado: ${state.connection.label()}", Modifier.weight(1f))
                    if (state.connection is ConnectionState.Failed) {
                        TextButton(onClick = vm::reconnect) { Text("Reintentar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverySection(state: RemoteUiState, vm: RemoteViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Buscar en la red", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (state.discovery.isScanning) {
                    OutlinedButton(onClick = vm::stopDiscovery) { Text("Detener") }
                } else {
                    Button(onClick = vm::startDiscovery) { Text("Buscar") }
                }
            }
            state.discovery.devices.forEach { device ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${device.name}\n${device.kind?.name ?: "?"} · ${device.host}", Modifier.weight(1f))
                    TextButton(onClick = { vm.addDiscoveredDevice(device) }, enabled = device.kind != null) {
                        Text("Añadir")
                    }
                }
            }
        }
    }
}

@Composable
private fun RemotePad(state: RemoteUiState, send: (RemoteCommand) -> Unit) {
    val caps = state.capabilities
    val all = caps.isEmpty()
    fun has(c: RemoteCapability) = all || c in caps

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (has(RemoteCapability.POWER)) {
            Button(onClick = { send(RemoteCommand.Power) }, Modifier.fillMaxWidth()) { Text("⏻ Encender / Apagar") }
        }
        if (has(RemoteCapability.NAVIGATION)) {
            KeyRow("Atrás" to RemoteCommand.Back, "Inicio" to RemoteCommand.Home, "Menú" to RemoteCommand.Menu, send = send)
            KeyRow("▲" to RemoteCommand.DpadUp, send = send)
            KeyRow("◀" to RemoteCommand.DpadLeft, "OK" to RemoteCommand.DpadCenter, "▶" to RemoteCommand.DpadRight, send = send)
            KeyRow("▼" to RemoteCommand.DpadDown, send = send)
        }
        if (has(RemoteCapability.VOLUME)) {
            KeyRow("Vol −" to RemoteCommand.VolumeDown, "Silencio" to RemoteCommand.Mute, "Vol +" to RemoteCommand.VolumeUp, send = send)
        }
        if (has(RemoteCapability.CHANNELS)) {
            KeyRow("Canal −" to RemoteCommand.ChannelDown, "Canal +" to RemoteCommand.ChannelUp, send = send)
        }
        if (has(RemoteCapability.MEDIA)) {
            KeyRow("⏪" to RemoteCommand.Rewind, "⏯" to RemoteCommand.PlayPause, "⏩" to RemoteCommand.FastForward, send = send)
        }
        if (has(RemoteCapability.DIGITS)) {
            (1..9).chunked(3).forEach { row ->
                KeyRow(*row.map { "$it" to RemoteCommand.Digit(it) }.toTypedArray(), send = send)
            }
            KeyRow("0" to RemoteCommand.Digit(0), send = send)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun KeyRow(vararg keys: Pair<String, RemoteCommand>, send: (RemoteCommand) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { (label, cmd) ->
            FilledTonalButton(onClick = { send(cmd) }, Modifier.size(width = 100.dp, height = 52.dp)) { Text(label) }
        }
    }
}

@Composable
private fun PairingDialog(message: String, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Emparejar") },
        text = {
            Column {
                Text(message)
                OutlinedTextField(code, { code = it }, label = { Text("Código") })
            }
        },
        confirmButton = { TextButton(onClick = { onSubmit(code) }) { Text("Enviar") } },
    )
}

@Composable
private fun TextInputDialog(initial: String, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escribir en el TV") },
        text = { OutlinedTextField(text, { text = it }) },
        confirmButton = { TextButton(onClick = { onSend(text) }) { Text("Enviar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

private fun ConnectionState.label(): String = when (this) {
    ConnectionState.Idle -> "Inactivo"
    ConnectionState.Connecting -> "Conectando…"
    ConnectionState.Waking -> "Despertando…"
    is ConnectionState.AwaitingPairing -> "Esperando emparejamiento"
    ConnectionState.Connected -> "Conectado"
    is ConnectionState.Reconnecting -> "Reconectando (intento $attempt)"
    is ConnectionState.Failed -> "Error: ${error.message}"
}
