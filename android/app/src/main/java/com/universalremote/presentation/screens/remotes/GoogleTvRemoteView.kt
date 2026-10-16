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
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.StreamingApp
import com.universalremote.presentation.theme.CyberColors

@Composable
fun GoogleTvRemoteView(
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier,
) {

    Surface(
        color = CyberColors.SurfaceContainerLowest,
        shape = RoundedCornerShape(36.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CyberColors.OutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(36.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Insignia Google TV
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "GOOGLE TV REMOTE",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberColors.LaserCyan,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "Chromecast",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 10.sp,
                    color = CyberColors.OnSurfaceVariant,
                )
            }

            // D-Pad Circular Minimalista de Google TV
            GoogleTvCircularDpad(onCommand = onCommand)

            Spacer(modifier = Modifier.height(2.dp))

            // Cuadrícula 2x2: Atrás, Google Assistant, Home, Mute
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GoogleTvCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Atrás",
                        onClick = { onCommand(RemoteCommand.Back) },
                    )

                    // Google Assistant Button (Destacado)
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(CyberColors.SurfaceContainerHigh)
                            .border(1.dp, CyberColors.LaserCyan.copy(alpha = 0.5f), CircleShape)
                            .clickable { onCommand(RemoteCommand.Search) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Asistente de Google", tint = CyberColors.LaserCyan, modifier = Modifier.size(24.dp))
                    }

                    GoogleTvCircleButton(
                        icon = Icons.Default.Home,
                        contentDescription = "Inicio",
                        onClick = { onCommand(RemoteCommand.Home) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    GoogleTvCircleButton(
                        icon = Icons.AutoMirrored.Filled.VolumeOff,
                        contentDescription = "Silencio",
                        onClick = { onCommand(RemoteCommand.Mute) },
                    )

                    // Control de volumen integrado tipo Google TV
                    Row(
                        modifier = Modifier
                            .height(50.dp)
                            .clip(RoundedCornerShape(25.dp))
                            .background(CyberColors.SurfaceContainerHigh)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onCommand(RemoteCommand.VolumeDown)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Bajar volumen", tint = CyberColors.OnSurface, modifier = Modifier.size(16.dp))
                        }

                        Text(
                            text = "VOL",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberColors.LaserCyan,
                        )

                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable {
                                    onCommand(RemoteCommand.VolumeUp)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Subir volumen", tint = CyberColors.OnSurface, modifier = Modifier.size(16.dp))
                        }
                    }

                    GoogleTvCircleButton(
                        icon = Icons.AutoMirrored.Filled.Input,
                        contentDescription = "Entrada TV",
                        onClick = { onCommand(RemoteCommand.Input) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Botones de Streaming de Google TV (YouTube y Netflix)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = CyberColors.SurfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .border(1.dp, CyberColors.YouTubeRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable { onCommand(RemoteCommand.LaunchApp(StreamingApp.YOUTUBE)) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Default.SmartDisplay, contentDescription = "YouTube", tint = CyberColors.YouTubeRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("YouTube", fontWeight = FontWeight.Bold, color = CyberColors.OnSurface, fontSize = 12.sp)
                    }
                }

                Surface(
                    color = CyberColors.SurfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .border(1.dp, CyberColors.NetflixRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable { onCommand(RemoteCommand.LaunchApp(StreamingApp.NETFLIX)) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("N", fontWeight = FontWeight.Bold, color = CyberColors.NetflixRed, fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Netflix", fontWeight = FontWeight.Bold, color = CyberColors.OnSurface, fontSize = 12.sp)
                    }
                }
            }

            // Botón de Encendido TV en la parte inferior
            Button(
                onClick = { onCommand(RemoteCommand.Power) },
                colors = ButtonDefaults.buttonColors(containerColor = CyberColors.SurfaceContainerHigh),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .border(1.dp, CyberColors.PowerRed.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = CyberColors.PowerRed, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Encender / Apagar TV", color = CyberColors.PowerRed, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun GoogleTvCircularDpad(onCommand: (RemoteCommand) -> Unit) {
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
                .size(width = 72.dp, height = 44.dp)
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
                .size(width = 72.dp, height = 44.dp)
                .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
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
                .size(width = 44.dp, height = 72.dp)
                .clip(RoundedCornerShape(topStart = 34.dp, bottomStart = 34.dp))
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
                .size(width = 44.dp, height = 72.dp)
                .clip(RoundedCornerShape(topEnd = 34.dp, bottomEnd = 34.dp))
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
                .border(1.dp, CyberColors.LaserCyan.copy(alpha = 0.3f), CircleShape)
                .clickable { onCommand(RemoteCommand.DpadCenter) },
            contentAlignment = Alignment.Center,
        ) {
            Text("OK", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = CyberColors.LaserCyan)
        }
    }
}

@Composable
private fun GoogleTvCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(CyberColors.SurfaceContainerHigh)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = CyberColors.OnSurface, modifier = Modifier.size(22.dp))
    }
}
