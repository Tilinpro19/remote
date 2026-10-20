package com.universalremote.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.domain.model.DiscoveredDevice
import com.universalremote.domain.model.RemoteError
import com.universalremote.presentation.RemoteUiState
import com.universalremote.presentation.theme.CyberColors

@Composable
fun DevicesScreen(
    state: RemoteUiState,
    onSelectDevice: (String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onAddDiscovered: (DiscoveredDevice) -> Unit,
    onAddManual: (String, DeviceKind) -> Unit,
    onOpenRemoteTab: () -> Unit,
    onReconnect: () -> Unit,
    onResetPairing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingRemoval by remember { mutableStateOf<DeviceProfile?>(null) }
    var showManualAdd by remember { mutableStateOf(false) }
    var manualHost by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf(DeviceKind.ANDROID_TV) }

    val infiniteTransition = rememberInfiniteTransition(label = "scanPulse")
    val scanAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scanAlpha",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Tarjeta principal de búsqueda en Wi-Fi
        Surface(
            color = CyberColors.SurfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Buscar en tu red Wi-Fi",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyberColors.OnSurface,
                        )
                        Text(
                            text = if (state.discovery.isScanning) "Buscando Smart TVs cercanos..." else "Encuentra tus Smart TVs en 1 toque",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberColors.OnSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = if (state.discovery.isScanning) onStopScan else onStartScan,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.discovery.isScanning) CyberColors.PowerRed else CyberColors.PrimaryContainer,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(
                            imageVector = if (state.discovery.isScanning) Icons.Default.Stop else Icons.Default.Search,
                            contentDescription = null,
                            tint = if (state.discovery.isScanning) Color.White else CyberColors.OnPrimaryContainer,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.discovery.isScanning) "Detener" else "Buscar",
                            fontWeight = FontWeight.Bold,
                            color = if (state.discovery.isScanning) Color.White else CyberColors.OnPrimaryContainer,
                        )
                    }
                }

                if (state.discovery.isScanning) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                        color = CyberColors.LaserCyan,
                        trackColor = CyberColors.SurfaceContainerHigh,
                    )
                }
            }
        }

        // 2. Televisores guardados del usuario
        Text(
            text = "Mis Televisores (${state.devices.size})",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = CyberColors.OnSurface,
        )

        if (state.devices.isEmpty()) {
            Surface(
                color = CyberColors.SurfaceContainerLowest,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = CyberColors.Outline,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aún no tienes televisores guardados",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = CyberColors.OnSurface,
                    )
                    Text(
                        text = "Toca el botón 'Buscar' arriba para conectar tu televisor automáticamente.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberColors.OnSurfaceVariant,
                    )
                }
            }
        } else {
            state.devices.forEach { device ->
                val isActive = device.id == state.activeDevice?.id
                Surface(
                    color = CyberColors.SurfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (isActive) 1.dp else 0.dp,
                            color = if (isActive) CyberColors.LaserCyan else Color.Transparent,
                            shape = RoundedCornerShape(14.dp),
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyberColors.SurfaceContainerHigh),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = if (isActive) CyberColors.LaserCyan else CyberColors.OnSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }

                            Column {
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = CyberColors.OnSurface,
                                )
                                val failed = (state.connection as? ConnectionState.Failed).takeIf { isActive }
                                Text(
                                    text = if (isActive) state.connection.statusLabel() else "Guardado",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 11.sp,
                                    color = when {
                                        failed != null -> CyberColors.PowerRed
                                        isActive -> CyberColors.Tertiary
                                        else -> CyberColors.OnSurfaceVariant
                                    },
                                )
                                if (failed != null) {
                                    Row {
                                        TextButton(onClick = onReconnect) { Text("Reintentar", color = CyberColors.LaserCyan) }
                                        // Certificado del TV cambiado (reset o firmware): hay que volver a emparejar.
                                        if (failed.error is RemoteError.Tls && device.kind == DeviceKind.ANDROID_TV) {
                                            TextButton(onClick = onResetPairing) { Text("Vincular de nuevo", color = CyberColors.LaserCyan) }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Button(
                                onClick = {
                                    onSelectDevice(device.id)
                                    onOpenRemoteTab()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isActive) CyberColors.PrimaryContainer else CyberColors.SurfaceContainerHigh,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = if (isActive) "Usar" else "Conectar",
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) CyberColors.OnPrimaryContainer else CyberColors.OnSurface,
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyberColors.SurfaceContainerHigh)
                                    .clickable { pendingRemoval = device },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Eliminar",
                                    tint = CyberColors.PowerRed,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Resultados de escaneo en vivo
        if (state.discovery.devices.isNotEmpty()) {
            Text(
                text = "Televisores detectados en tu red (${state.discovery.devices.size})",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = CyberColors.LaserCyan,
                modifier = Modifier.padding(top = 4.dp),
            )

            state.discovery.devices.forEach { discovered ->
                Surface(
                    color = CyberColors.SurfaceContainerLow,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = discovered.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = CyberColors.OnSurface,
                            )
                            Text(
                                text = "${discovered.kind?.displayName() ?: "Smart TV"} • ${discovered.host}",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                color = CyberColors.OnSurfaceVariant,
                            )
                        }

                        // Sin tipo reconocido no hay adaptador con el que controlarlo.
                        if (discovered.kind != null) Button(
                            onClick = { onAddDiscovered(discovered) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberColors.PrimaryContainer),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = CyberColors.OnPrimaryContainer, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Añadir", fontWeight = FontWeight.Bold, color = CyberColors.OnPrimaryContainer)
                        }
                    }
                }
            }
        }

        // 4. Acordeón simple: ¿No aparece tu TV? Añadir por IP
        Surface(
            color = CyberColors.SurfaceContainerLowest,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showManualAdd = !showManualAdd }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "¿No aparece tu televisor en la lista?",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CyberColors.OnSurfaceVariant,
                    )
                    Icon(
                        imageVector = if (showManualAdd) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = CyberColors.OnSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                AnimatedVisibility(visible = showManualAdd) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "Puedes escribir la dirección IP de tu TV (puedes verla en los Ajustes de Red de tu televisor):",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberColors.OnSurfaceVariant,
                        )

                        OutlinedTextField(
                            value = manualHost,
                            onValueChange = { manualHost = it },
                            label = { Text("IP del TV (ej. 192.168.1.50)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberColors.LaserCyan,
                                unfocusedBorderColor = CyberColors.OutlineVariant,
                            ),
                        )

                        Text(
                            text = "Marca del televisor:",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberColors.OnSurface,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            listOf(
                                DeviceKind.ANDROID_TV to "Google TV",
                                DeviceKind.SAMSUNG_TIZEN to "Samsung",
                                DeviceKind.LG_WEBOS to "LG",
                                DeviceKind.ROKU to "Roku",
                            ).forEach { (kind, label) ->
                                val isSelected = kind == selectedBrand
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) CyberColors.LaserCyan.copy(alpha = 0.2f)
                                            else CyberColors.SurfaceContainerHigh
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) CyberColors.LaserCyan else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                        .clickable { selectedBrand = kind }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = FontFamily.SansSerif,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) CyberColors.LaserCyan else CyberColors.OnSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (manualHost.isNotBlank()) {
                                    onAddManual(manualHost, selectedBrand)
                                    manualHost = ""
                                    showManualAdd = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberColors.PrimaryContainer),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Guardar televisor", fontWeight = FontWeight.Bold, color = CyberColors.OnPrimaryContainer)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    pendingRemoval?.let { device ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("¿Eliminar ${device.name}?") },
            text = { Text("Tendrás que volver a buscarlo y, si lo pide, vincularlo otra vez.") },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveDevice(device.id)
                    pendingRemoval = null
                }) { Text("Eliminar", color = CyberColors.PowerRed) }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancelar") } },
        )
    }
}

private fun ConnectionState.statusLabel(): String = when (this) {
    ConnectionState.Idle -> "● Televisor activo"
    ConnectionState.Connecting -> "Conectando…"
    ConnectionState.Waking -> "Encendiendo el televisor…"
    is ConnectionState.AwaitingPairing -> "Esperando vinculación"
    ConnectionState.Connected -> "● Conectado"
    is ConnectionState.Reconnecting -> "Reconectando…"
    // El mensaje de RemoteError ya es una frase completa.
    is ConnectionState.Failed -> error.message ?: "No se pudo conectar"
}

private fun DeviceKind.displayName(): String = when (this) {
    DeviceKind.ROKU -> "Roku"
    DeviceKind.ANDROID_TV -> "Android / Google TV"
    DeviceKind.LG_WEBOS -> "LG webOS"
    DeviceKind.SAMSUNG_TIZEN -> "Samsung Smart TV"
    DeviceKind.SONY_BRAVIA -> "Sony Bravia"
    DeviceKind.VIZIO -> "Vizio SmartCast"
    DeviceKind.PHILIPS_JOINTSPACE -> "Philips"
    DeviceKind.FIRE_TV -> "Amazon Fire TV"
    DeviceKind.HISENSE_VIDAA -> "Hisense VIDAA"
    DeviceKind.INFRARED -> "Televisor Clásico (IR)"
    DeviceKind.BLUETOOTH_HID -> "Bluetooth"
}
