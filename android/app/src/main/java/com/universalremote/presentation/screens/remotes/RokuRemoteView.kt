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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
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

private val RokuPurple = Color(0xFF673AB7)
private val RokuPurpleBright = Color(0xFF8B5CF6)

@Composable
fun RokuRemoteView(
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var volume by remember { mutableIntStateOf(20) }

    Surface(
        color = CyberColors.SurfaceContainerLowest,
        shape = RoundedCornerShape(32.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, RokuPurple.copy(alpha = 0.4f), RoundedCornerShape(32.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Título icónico de Roku
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ROKU VOICE REMOTE",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RokuPurpleBright,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "Pro Edition",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 10.sp,
                    color = CyberColors.OnSurfaceVariant,
                )
            }

            // Fila Superior: Encendido TV y Micrófono Roku
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // TV Power
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyberColors.SurfaceContainerHigh)
                        .clickable { onCommand(RemoteCommand.Power) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "Encender / Apagar TV", tint = CyberColors.PowerRed, modifier = Modifier.size(22.dp))
                }

                // Voice Mic
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(RokuPurple.copy(alpha = 0.25f))
                        .border(1.dp, RokuPurpleBright, CircleShape)
                        .clickable { onCommand(RemoteCommand.Search) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "Búsqueda por voz Roku", tint = RokuPurpleBright, modifier = Modifier.size(22.dp))
                }
            }

            // Fila de Atrás y Home (Roku Back & Home)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RokuButton(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") { onCommand(RemoteCommand.Back) }
                RokuButton(Icons.Default.Home, "Home", tint = RokuPurpleBright) { onCommand(RemoteCommand.Home) }
            }

            // Rueda D-Pad Morada Icónica de Roku
            RokuDirectionalPad(onCommand = onCommand)

            // Fila de Replay (Instant Replay) y Asterisco Options (*)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RokuButton(Icons.Default.Replay, "Replay -10s") { onCommand(RemoteCommand.Rewind) }

                // Asterisco Roku (*)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyberColors.SurfaceContainerHigh)
                        .clickable { onCommand(RemoteCommand.Menu) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("*", fontFamily = FontFamily.SansSerif, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = CyberColors.OnSurface)
                }
            }

            // Barra Multimedia Roku (Rewind, Play/Pause, Fast Forward)
            Surface(
                color = CyberColors.SurfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RokuMediaButton(Icons.Default.FastRewind, "Rebobinar") { onCommand(RemoteCommand.Rewind) }
                    RokuMediaButton(Icons.Default.PlayArrow, "Play / Pausa", isPrimary = true) { onCommand(RemoteCommand.PlayPause) }
                    RokuMediaButton(Icons.Default.FastForward, "Avanzar") { onCommand(RemoteCommand.FastForward) }
                }
            }

            // Control de Volumen y Silencio de Roku
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberColors.SurfaceContainerHigh)
                            .clickable {
                                if (volume > 0) volume -= 1
                                onCommand(RemoteCommand.VolumeDown)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Bajar volumen", tint = CyberColors.OnSurface, modifier = Modifier.size(18.dp))
                    }

                    Text(
                        text = "VOL $volume",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RokuPurpleBright,
                    )

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberColors.SurfaceContainerHigh)
                            .clickable {
                                volume = (volume + 1).coerceAtMost(100)
                                onCommand(RemoteCommand.VolumeUp)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Subir volumen", tint = CyberColors.OnSurface, modifier = Modifier.size(18.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberColors.SurfaceContainerHigh)
                        .clickable { onCommand(RemoteCommand.Mute) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "Silencio", tint = CyberColors.OnSurface, modifier = Modifier.size(18.dp))
                }
            }

            // Botones de Streaming Icónicos de Roku (Netflix, Disney+, Apple TV, The Roku Channel)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RokuAppButton("NETFLIX", CyberColors.NetflixRed, Modifier.weight(1f)) { onCommand(RemoteCommand.LaunchApp(StreamingApp.NETFLIX)) }
                    RokuAppButton("Disney+", CyberColors.DisneyBlue, Modifier.weight(1f)) { onCommand(RemoteCommand.LaunchApp(StreamingApp.DISNEY_PLUS)) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RokuAppButton("Apple TV", CyberColors.OnSurface, Modifier.weight(1f)) { onCommand(RemoteCommand.LaunchApp(StreamingApp.APPLE_TV)) }
                    RokuAppButton("Roku Channel", RokuPurpleBright, Modifier.weight(1f)) { onCommand(RemoteCommand.LaunchApp(StreamingApp.ROKU_CHANNEL)) }
                }
            }
        }
    }
}

@Composable
private fun RokuDirectionalPad(onCommand: (RemoteCommand) -> Unit) {
    Box(
        modifier = Modifier
            .size(195.dp)
            .clip(CircleShape)
            .background(RokuPurple.copy(alpha = 0.15f))
            .border(1.dp, RokuPurple.copy(alpha = 0.5f), CircleShape)
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
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Arriba", tint = RokuPurpleBright, modifier = Modifier.size(28.dp))
        }

        // Abajo
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 72.dp, height = 44.dp)
                .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
                .background(CyberColors.SurfaceContainerHigh)
                .clickable { onCommand(RemoteCommand.DpadDown) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Abajo", tint = RokuPurpleBright, modifier = Modifier.size(28.dp))
        }

        // Izquierda
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 44.dp, height = 72.dp)
                .clip(RoundedCornerShape(topStart = 36.dp, bottomStart = 36.dp))
                .background(CyberColors.SurfaceContainerHigh)
                .clickable { onCommand(RemoteCommand.DpadLeft) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Izquierda", tint = RokuPurpleBright, modifier = Modifier.size(28.dp))
        }

        // Derecha
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 44.dp, height = 72.dp)
                .clip(RoundedCornerShape(topEnd = 36.dp, bottomEnd = 36.dp))
                .background(CyberColors.SurfaceContainerHigh)
                .clickable { onCommand(RemoteCommand.DpadRight) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Derecha", tint = RokuPurpleBright, modifier = Modifier.size(28.dp))
        }

        // OK Central Roku
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(RokuPurple)
                .border(1.dp, RokuPurpleBright, CircleShape)
                .clickable { onCommand(RemoteCommand.DpadCenter) },
            contentAlignment = Alignment.Center,
        ) {
            Text("OK", fontFamily = FontFamily.SansSerif, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun RokuButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = CyberColors.OnSurface,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(CyberColors.SurfaceContainerHigh)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun RokuMediaButton(
    icon: ImageVector,
    contentDescription: String,
    isPrimary: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(if (isPrimary) 46.dp else 40.dp)
            .clip(CircleShape)
            .background(if (isPrimary) RokuPurple else CyberColors.SurfaceContainerHigh)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isPrimary) Color.White else CyberColors.OnSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun RokuAppButton(
    name: String,
    brandColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = CyberColors.SurfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .height(42.dp)
            .border(1.dp, brandColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = brandColor,
            )
        }
    }
}
