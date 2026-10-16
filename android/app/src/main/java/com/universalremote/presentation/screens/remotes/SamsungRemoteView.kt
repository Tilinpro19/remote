package com.universalremote.presentation.screens.remotes

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.StreamingApp
import com.universalremote.presentation.theme.CyberColors

@Composable
fun SamsungRemoteView(
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var volume by remember { mutableIntStateOf(24) }
    var channel by remember { mutableIntStateOf(12) }

    Surface(
        color = CyberColors.SurfaceContainerLowest,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CyberColors.OutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(28.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Insignia del modelo Samsung One Remote
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SAMSUNG ONE REMOTE",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberColors.LaserCyan,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "SolarCell",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 10.sp,
                    color = CyberColors.OnSurfaceVariant,
                )
            }

            // Fila Superior: Botón de Encendido, Micrófono y 123 (Numpad)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SamsungCircleButton(
                    icon = Icons.Default.PowerSettingsNew,
                    iconTint = CyberColors.PowerRed,
                    contentDescription = "Encender / Apagar",
                    onClick = { onCommand(RemoteCommand.Power) },
                )

                SamsungCircleButton(
                    icon = Icons.Default.Mic,
                    iconTint = CyberColors.OnSurface,
                    contentDescription = "Asistente de voz",
                    onClick = { onCommand(RemoteCommand.Search) },
                )

                // Botón 123 para teclado numérico
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyberColors.SurfaceContainerHigh)
                        .clickable { onCommand(RemoteCommand.Menu) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "123",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberColors.LaserCyan,
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Rueda D-Pad Circular Samsung
            SamsungCircularDpad(onCommand = onCommand)

            Spacer(modifier = Modifier.height(4.dp))

            // Satélites Samsung: Atrás, Home (Smart Hub), Play/Pausa
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SamsungCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    iconTint = CyberColors.OnSurface,
                    contentDescription = "Atrás",
                    onClick = { onCommand(RemoteCommand.Back) },
                )

                SamsungCircleButton(
                    icon = Icons.Default.Home,
                    iconTint = CyberColors.LaserCyan,
                    contentDescription = "Inicio Smart Hub",
                    onClick = { onCommand(RemoteCommand.Home) },
                )

                SamsungCircleButton(
                    icon = Icons.Default.PlayArrow,
                    iconTint = CyberColors.OnSurface,
                    contentDescription = "Reproducir / Pausa",
                    onClick = { onCommand(RemoteCommand.PlayPause) },
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Balancines Táctiles Metálicos de Volumen y Canal (Icónicos de Samsung)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SamsungRockerSwitch(
                    label = "VOL",
                    valueText = "$volume",
                    onUp = {
                        volume = (volume + 1).coerceAtMost(100)
                        onCommand(RemoteCommand.VolumeUp)
                    },
                    onDown = {
                        volume = (volume - 1).coerceAtLeast(0)
                        onCommand(RemoteCommand.VolumeDown)
                    },
                    onPressCenter = { onCommand(RemoteCommand.Mute) },
                )

                SamsungRockerSwitch(
                    label = "CH",
                    valueText = "$channel",
                    onUp = {
                        channel += 1
                        onCommand(RemoteCommand.ChannelUp)
                    },
                    onDown = {
                        if (channel > 1) channel -= 1
                        onCommand(RemoteCommand.ChannelDown)
                    },
                    onPressCenter = { onCommand(RemoteCommand.Info) },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Accesos Directos a Streaming Samsung (Netflix, Prime, Disney+, TV Plus)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SamsungAppPill("NETFLIX", CyberColors.NetflixRed, Modifier.weight(1f)) {
                        onCommand(RemoteCommand.LaunchApp(StreamingApp.NETFLIX))
                    }
                    SamsungAppPill("prime video", CyberColors.PrimeBlue, Modifier.weight(1f)) {
                        onCommand(RemoteCommand.LaunchApp(StreamingApp.PRIME_VIDEO))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SamsungAppPill("Disney+", CyberColors.DisneyBlue, Modifier.weight(1f)) {
                        onCommand(RemoteCommand.LaunchApp(StreamingApp.DISNEY_PLUS))
                    }
                    SamsungAppPill("TV Plus", CyberColors.LaserCyan, Modifier.weight(1f)) {
                        onCommand(RemoteCommand.LaunchApp(StreamingApp.SAMSUNG_TV_PLUS))
                    }
                }
            }
        }
    }
}

@Composable
private fun SamsungCircularDpad(onCommand: (RemoteCommand) -> Unit) {
    Box(
        modifier = Modifier
            .size(195.dp)
            .clip(CircleShape)
            .background(CyberColors.SurfaceContainerLow)
            .border(1.dp, CyberColors.OutlineVariant.copy(alpha = 0.3f), CircleShape)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Arriba
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 72.dp, height = 46.dp)
                .clip(RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .background(CyberColors.SurfaceContainerHigh)
                .clickable { onCommand(RemoteCommand.DpadUp) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Arriba", tint = CyberColors.OnSurface, modifier = Modifier.size(26.dp))
        }

        // Abajo
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 72.dp, height = 46.dp)
                .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(CyberColors.SurfaceContainerHigh)
                .clickable { onCommand(RemoteCommand.DpadDown) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Abajo", tint = CyberColors.OnSurface, modifier = Modifier.size(26.dp))
        }

        // Izquierda
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 46.dp, height = 72.dp)
                .clip(RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp))
                .background(CyberColors.SurfaceContainerHigh)
                .clickable { onCommand(RemoteCommand.DpadLeft) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Izquierda", tint = CyberColors.OnSurface, modifier = Modifier.size(26.dp))
        }

        // Derecha
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 46.dp, height = 72.dp)
                .clip(RoundedCornerShape(topEnd = 36.dp, bottomEnd = 36.dp))
                .background(CyberColors.SurfaceContainerHigh)
                .clickable { onCommand(RemoteCommand.DpadRight) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Derecha", tint = CyberColors.OnSurface, modifier = Modifier.size(26.dp))
        }

        // Botón Select Central
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(CyberColors.SurfaceContainerHighest)
                .border(1.dp, CyberColors.LaserCyan.copy(alpha = 0.35f), CircleShape)
                .clickable { onCommand(RemoteCommand.DpadCenter) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "OK",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = CyberColors.LaserCyan,
            )
        }
    }
}

@Composable
private fun SamsungCircleButton(
    icon: ImageVector,
    iconTint: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CyberColors.SurfaceContainerHigh)
            .border(1.dp, CyberColors.OutlineVariant.copy(alpha = 0.25f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SamsungRockerSwitch(
    label: String,
    valueText: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onPressCenter: () -> Unit,
) {
    Surface(
        color = CyberColors.SurfaceContainerLow,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .width(80.dp)
            .border(1.dp, CyberColors.OutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(22.dp)),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Palanca Arriba
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(CyberColors.SurfaceContainerHigh)
                    .clickable { onUp() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Subir $label", tint = CyberColors.OnSurface, modifier = Modifier.size(18.dp))
            }

            // Centro (pulsar para Mute o Info)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .clickable { onPressCenter() },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = label, fontFamily = FontFamily.SansSerif, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberColors.LaserCyan)
                    Text(text = valueText, fontFamily = FontFamily.SansSerif, fontSize = 9.sp, color = CyberColors.OnSurfaceVariant)
                }
            }

            // Palanca Abajo
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(CyberColors.SurfaceContainerHigh)
                    .clickable { onDown() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Bajar $label", tint = CyberColors.OnSurface, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SamsungAppPill(
    label: String,
    brandColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = CyberColors.SurfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
            .height(42.dp)
            .border(1.dp, brandColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .clickable { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = brandColor,
            )
        }
    }
}
