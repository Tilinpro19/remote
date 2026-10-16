package com.universalremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.universalremote.domain.model.RemoteError
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
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.PairingRequest
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
        // El título ya lo muestra la barra de la actividad: no se repite aquí.
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DevicesSection(state, vm)
            DiscoverySection(state, vm)
            ManualAddSection(vm)
            if (state.activeDevice != null) RemotePad(state) { vm.onCommandTriggered(it) }
        }
    }

    (state.connection as? ConnectionState.AwaitingPairing)?.let { pairing ->
        PairingDialog(pairing.request, onSubmit = vm::submitPairingCode)
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
            state.activeDevice?.let { device ->
                val failed = state.connection as? ConnectionState.Failed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.connection.label(),
                        Modifier.weight(1f),
                        color = if (failed != null) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                    if (failed != null) {
                        TextButton(onClick = vm::reconnect) { Text("Reintentar") }
                        if (failed.error is RemoteError.Tls && device.kind == DeviceKind.ANDROID_TV) {
                            TextButton(onClick = vm::resetPairing) { Text("Vincular") }
                        }
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
                    Text("${device.name}\n${device.kind?.label() ?: "Sin control compatible"} · ${device.host}", Modifier.weight(1f))
                    TextButton(onClick = { vm.addDiscoveredDevice(device) }, enabled = device.kind != null) {
                        Text("Añadir")
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualAddSection(vm: RemoteViewModel) {
    var host by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Añadir por IP", style = MaterialTheme.typography.titleMedium)
            Text("Si la búsqueda no encuentra tu TV, escribe su IP y elige la marca.")
            OutlinedTextField(host, { host = it }, label = { Text("IP del TV (p. ej. 192.168.1.40)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    "Android/Google TV" to DeviceKind.ANDROID_TV,
                    "LG" to DeviceKind.LG_WEBOS,
                    "Samsung" to DeviceKind.SAMSUNG_TIZEN,
                    "Roku" to DeviceKind.ROKU,
                ).forEach { (label, kind) ->
                    OutlinedButton(onClick = { vm.addManualDevice(host, kind) }) { Text(label, style = MaterialTheme.typography.labelSmall) }
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
            Button(onClick = { send(RemoteCommand.Power) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Encender / Apagar")
            }
        }
        if (has(RemoteCapability.NAVIGATION)) {
            KeyRow(Key("Atrás", RemoteCommand.Back), Key("Inicio", RemoteCommand.Home), Key("Menú", RemoteCommand.Menu), send = send)
            KeyRow(Key("Arriba", RemoteCommand.DpadUp, Icons.Default.KeyboardArrowUp), send = send)
            KeyRow(
                Key("Izquierda", RemoteCommand.DpadLeft, Icons.AutoMirrored.Filled.KeyboardArrowLeft),
                Key("OK", RemoteCommand.DpadCenter),
                Key("Derecha", RemoteCommand.DpadRight, Icons.AutoMirrored.Filled.KeyboardArrowRight),
                send = send,
            )
            KeyRow(Key("Abajo", RemoteCommand.DpadDown, Icons.Default.KeyboardArrowDown), send = send)
        }
        if (has(RemoteCapability.VOLUME)) {
            KeyRow(Key("Vol −", RemoteCommand.VolumeDown), Key("Silencio", RemoteCommand.Mute), Key("Vol +", RemoteCommand.VolumeUp), send = send)
        }
        if (has(RemoteCapability.CHANNELS)) {
            KeyRow(Key("Canal −", RemoteCommand.ChannelDown), Key("Canal +", RemoteCommand.ChannelUp), send = send)
        }
        if (has(RemoteCapability.MEDIA)) {
            KeyRow(
                Key("Rebobinar", RemoteCommand.Rewind, Icons.Default.FastRewind),
                Key("Reproducir / Pausa", RemoteCommand.PlayPause, Icons.Default.PlayArrow, Icons.Default.Pause),
                Key("Avanzar", RemoteCommand.FastForward, Icons.Default.FastForward),
                send = send,
            )
        }
        if (has(RemoteCapability.DIGITS)) {
            (1..9).chunked(3).forEach { row ->
                KeyRow(*row.map { Key("$it", RemoteCommand.Digit(it)) }.toTypedArray(), send = send)
            }
            KeyRow(Key("0", RemoteCommand.Digit(0)), send = send)
        }
        // Margen para poder desplazar la última fila por encima del Snackbar.
        Spacer(Modifier.height(88.dp))
    }
}

/** Tecla con texto o, si tiene iconos, sólo iconos vectoriales ([label] queda como descripción accesible). */
private class Key(val label: String, val command: RemoteCommand, vararg val icons: ImageVector)

@Composable
private fun KeyRow(vararg keys: Key, send: (RemoteCommand) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { key ->
            FilledTonalButton(
                onClick = { send(key.command) },
                Modifier.size(width = 100.dp, height = 48.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                if (key.icons.isEmpty()) {
                    Text(key.label, maxLines = 1, softWrap = false)
                } else {
                    key.icons.forEachIndexed { i, icon ->
                        Icon(icon, contentDescription = if (i == 0) key.label else null)
                    }
                }
            }
        }
    }
}

@Composable
private fun PairingDialog(request: PairingRequest, onSubmit: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    val needsCode = request.method is PairingRequest.Method.Pin
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Emparejar") },
        text = {
            Column {
                Text(request.message)
                if (needsCode) OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Código") }, singleLine = true)
            }
        },
        // En "confirmar en el TV" el adaptador pasa a Conectado solo; el diálogo desaparece entonces.
        confirmButton = {
            if (needsCode) TextButton(onClick = { onSubmit(code) }) { Text("Enviar") }
        },
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
    ConnectionState.Idle -> "Estado: Inactivo"
    ConnectionState.Connecting -> "Estado: Conectando…"
    ConnectionState.Waking -> "Estado: Despertando…"
    is ConnectionState.AwaitingPairing -> "Requiere vinculación (introduce el PIN)"
    ConnectionState.Connected -> "Estado: Conectado"
    is ConnectionState.Reconnecting -> "Estado: Reconectando (intento $attempt)"
    // El mensaje de RemoteError ya es una frase completa: sin prefijos "Error:" encadenados.
    is ConnectionState.Failed -> error.message ?: "Error de conexión"
}

private fun DeviceKind.label(): String = when (this) {
    DeviceKind.ROKU -> "Roku"
    DeviceKind.ANDROID_TV -> "Android TV"
    DeviceKind.LG_WEBOS -> "LG webOS"
    DeviceKind.SAMSUNG_TIZEN -> "Samsung"
    DeviceKind.INFRARED -> "IR"
    DeviceKind.BLUETOOTH_HID -> "Bluetooth"
}
