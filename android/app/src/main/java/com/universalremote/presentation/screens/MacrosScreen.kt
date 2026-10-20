package com.universalremote.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.universalremote.domain.model.Macro
import com.universalremote.presentation.MacrosUiState
import com.universalremote.presentation.label
import com.universalremote.presentation.theme.CyberColors

/**
 * Macros del TV activo. Para grabar: pulsar "Grabar", usar el mando en cualquier pestaña y volver
 * aquí para guardar. Cada paso conserva la pausa real entre pulsaciones.
 */
@Composable
fun MacrosScreen(
    state: MacrosUiState,
    onStartRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    onSaveRecording: (String) -> Unit,
    onRun: (Macro) -> Unit,
    onStop: () -> Unit,
    onDelete: (Macro) -> Unit,
    modifier: Modifier = Modifier,
) {
    var naming by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Macro?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Macros y escenas",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = CyberColors.OnSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (!state.hasActiveDevice) {
            item { Hint("Elige un televisor en Dispositivos para crear macros.") }
            return@LazyColumn
        }

        item {
            val recording = state.recording
            if (recording == null) {
                Button(onClick = onStartRecording, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                    Text("  Grabar macro nueva")
                }
            } else {
                RecordingCard(
                    steps = recording.map { it.command.label() },
                    onSave = { naming = true },
                    onCancel = onCancelRecording,
                )
            }
        }

        state.message?.let { msg -> item { Hint(msg) } }

        if (state.macros.isEmpty() && state.recording == null) {
            item { Hint("Aún no hay macros. Graba una secuencia, por ejemplo: encender, entrada HDMI, Netflix.") }
        }

        items(state.macros, key = { it.id }) { macro ->
            MacroRow(
                macro = macro,
                running = state.runningId == macro.id,
                enabled = state.runningId == null,
                onRun = { onRun(macro) },
                onStop = onStop,
                onDelete = { pendingDelete = macro },
            )
        }
    }

    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("Nombre de la macro") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(MAX_NAME) }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveRecording(name)
                        naming = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Seguir grabando") } },
        )
    }

    pendingDelete?.let { macro ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("¿Borrar «${macro.name}»?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(macro)
                        pendingDelete = null
                    },
                ) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun RecordingCard(steps: List<String>, onSave: () -> Unit, onCancel: () -> Unit) {
    Surface(color = CyberColors.SurfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = CyberColors.PowerRed)
                Text("  Grabando: usa el mando en cualquier pestaña", color = CyberColors.OnSurface)
            }
            Text(
                text = if (steps.isEmpty()) "Sin pasos todavía" else steps.joinToString(" → "),
                color = CyberColors.OnSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, enabled = steps.isNotEmpty()) { Text("Guardar") }
                OutlinedButton(onClick = onCancel) { Text("Descartar") }
            }
        }
    }
}

@Composable
private fun MacroRow(
    macro: Macro,
    running: Boolean,
    enabled: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(color = CyberColors.SurfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(macro.name, color = CyberColors.OnSurface, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${macro.steps.size} pasos · " + macro.steps.joinToString(" → ") { it.command.label() },
                    color = CyberColors.OnSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (running) {
                IconButton(onClick = onStop) {
                    Icon(Icons.Default.Stop, contentDescription = "Detener", tint = CyberColors.PowerRed)
                }
            } else {
                IconButton(onClick = onRun, enabled = enabled) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Ejecutar", tint = CyberColors.Tertiary)
                }
                IconButton(onClick = onDelete, enabled = enabled) {
                    Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = CyberColors.OnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = CyberColors.OnSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
}

private const val MAX_NAME = 40
