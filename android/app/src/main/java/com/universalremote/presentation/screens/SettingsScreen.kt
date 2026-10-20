package com.universalremote.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.presentation.RemoteUiState
import com.universalremote.presentation.theme.CyberColors

@Composable
fun SettingsScreen(
    state: RemoteUiState,
    onSendCommand: (RemoteCommand) -> Unit,
    onHapticsChange: (Boolean) -> Unit,
    onVolumeKeysChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdvancedTools by remember { mutableStateOf(false) }
    var wolFeedbackMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Ajustes de la Aplicación",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = CyberColors.OnSurface,
        )

        // 1. Ajuste de Respuesta Háptica
        SettingToggleCard(
            icon = Icons.Default.Vibration,
            title = "Vibración al pulsar",
            subtitle = "Sensación táctil al tocar los botones",
            checked = state.hapticsEnabled,
            onCheckedChange = onHapticsChange,
        )

        SettingToggleCard(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = "Botones de volumen del teléfono",
            subtitle = "Controlan el volumen del TV mientras ves el mando",
            checked = state.volumeKeysEnabled,
            onCheckedChange = onVolumeKeysChange,
        )

        // 2. Encendido Remoto de Emergencia (Wake-on-LAN)
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
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberColors.SurfaceContainerHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = CyberColors.Tertiary,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Column {
                        Text(
                            text = "Encender televisor apagado",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = CyberColors.OnSurface,
                        )
                        Text(
                            text = "Envía una señal por red si tu TV está en modo reposo",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberColors.OnSurfaceVariant,
                        )
                    }
                }

                Button(
                    onClick = {
                        onSendCommand(RemoteCommand.PowerOn)
                        wolFeedbackMessage = if (state.activeDevice == null) null else "Encendiendo ${state.activeDevice.name}…"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberColors.SurfaceContainerHigh),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = CyberColors.Tertiary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Enviar señal de encendido",
                        fontWeight = FontWeight.Bold,
                        color = CyberColors.Tertiary,
                    )
                }

                if (wolFeedbackMessage != null) {
                    Text(
                        text = wolFeedbackMessage ?: "",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        color = CyberColors.Tertiary,
                    )
                }
            }
        }

        // 3. Hardware disponible en tu teléfono
        Surface(
            color = CyberColors.SurfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Conexiones de tu teléfono",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = CyberColors.OnSurface,
                )

                HardwareStatusItem(
                    icon = Icons.Default.Wifi,
                    title = "Red Wi-Fi",
                    subtitle = "Para televisores Smart TV (Samsung, LG, Google TV, Roku)",
                    isAvailable = true,
                )

                HardwareStatusItem(
                    icon = Icons.Default.Sensors,
                    title = "Infrarrojos (IR)",
                    subtitle = if (state.hasIrEmitter) "Sensor disponible para televisores clásicos" else "No disponible (se controlará por red Wi-Fi)",
                    isAvailable = state.hasIrEmitter,
                )
            }
        }

        // 4. Opciones Avanzadas (Ocultas por defecto para no abrumar al usuario)
        Surface(
            color = CyberColors.SurfaceContainerLowest,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvancedTools = !showAdvancedTools }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = CyberColors.OnSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Opciones para técnicos de red",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = CyberColors.OnSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = if (showAdvancedTools) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = CyberColors.OnSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }

                AnimatedVisibility(visible = showAdvancedTools) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Información de bajo nivel:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = CyberColors.LaserCyan,
                        )
                        Text(
                            text = "• Multicast Lock: Activo en interfaz Wi-Fi L2\n• Protocolos: Tizen wss://8002, webOS SSAP, Roku ECP REST, Android TV v2\n• Paquetes WoL: Broadcast UDP Puerto 9 con sincronía 6x0xFF",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = CyberColors.OnSurfaceVariant,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }

        // 5. Acerca de OmniRemote
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = CyberColors.Outline, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "OmniRemote v1.0.0 • Diseñado para ser fácil y rápido",
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = CyberColors.OnSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HardwareStatusItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isAvailable: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isAvailable) CyberColors.Tertiary else CyberColors.Outline,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                text = title,
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = CyberColors.OnSurface,
            )
            Text(
                text = subtitle,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = CyberColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        color = CyberColors.SurfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberColors.SurfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CyberColors.LaserCyan,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = CyberColors.OnSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberColors.OnSurfaceVariant,
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyberColors.OnPrimaryContainer,
                    checkedTrackColor = CyberColors.PrimaryContainer,
                    uncheckedThumbColor = CyberColors.OnSurfaceVariant,
                    uncheckedTrackColor = CyberColors.SurfaceContainerHigh,
                ),
            )
        }
    }
}
