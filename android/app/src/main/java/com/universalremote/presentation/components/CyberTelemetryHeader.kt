package com.universalremote.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalremote.R
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceProfile
import com.universalremote.presentation.theme.CyberColors

@Composable
fun CyberTelemetryHeader(
    subtitle: String,
    activeDevice: DeviceProfile?,
    connectionState: ConnectionState,
    poweredOn: Boolean? = null,
    devices: List<DeviceProfile> = emptyList(),
    onSelectDevice: (String) -> Unit = {},
    onDeviceClick: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Reconnecting

    val statusDotColor = when {
        isConnected -> CyberColors.EmeraldGreen
        isConnecting -> CyberColors.LaserCyan
        else -> CyberColors.PowerRed
    }

    val statusText = when {
        isConnected && poweredOn == false -> "En reposo"
        isConnected -> "Conectado"
        isConnecting -> "Conectando…"
        activeDevice != null -> "Listo"
        else -> "Sin TV"
    }

    Surface(
        color = CyberColors.Surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Logotipo de la app + Título + Subtítulo / Estado
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberColors.SurfaceContainerHigh)
                            .border(
                                1.dp,
                                CyberColors.LaserCyan.copy(alpha = 0.35f),
                                RoundedCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = "Universal Remote Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "Universal Remote",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.4.sp,
                            ),
                            color = CyberColors.Primary,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(top = 1.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .alpha(if (isConnecting) pulseAlpha else 1f)
                                    .clip(CircleShape)
                                    .background(statusDotColor),
                            )
                            Text(
                                text = "$statusText • $subtitle",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isConnected) CyberColors.Tertiary else CyberColors.OnSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Chip accesible del equipo actual: con varios TVs abre el selector rápido.
                Box {
                    Surface(
                        color = if (isConnected) CyberColors.LaserCyan.copy(alpha = 0.12f) else CyberColors.SurfaceContainerHigh,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isConnected) CyberColors.LaserCyan.copy(alpha = 0.4f) else CyberColors.OutlineVariant.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { if (devices.size > 1) menuOpen = true else onDeviceClick() },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = if (isConnected) Icons.Default.Wifi else Icons.Default.Tv,
                                contentDescription = null,
                                tint = if (isConnected) CyberColors.LaserCyan else CyberColors.OnSurfaceVariant,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = activeDevice?.name?.take(13) ?: "Elegir TV",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isConnected) CyberColors.Primary else CyberColors.OnSurface,
                                maxLines = 1,
                            )
                        }
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        devices.forEach { device ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = device.name,
                                        fontWeight = if (device.id == activeDevice?.id) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    if (device.id != activeDevice?.id) onSelectDevice(device.id)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Gestionar televisores…") },
                            onClick = {
                                menuOpen = false
                                onDeviceClick()
                            },
                        )
                    }
                }
            }

            // Línea divisoria sutil para delimitar el encabezado
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CyberColors.OutlineVariant.copy(alpha = 0.2f)),
            )
        }
    }
}
