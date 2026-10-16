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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalremote.domain.model.KeyColor
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.presentation.theme.CyberColors

@Composable
fun ClassicIrRemoteView(
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var volume by remember { mutableIntStateOf(25) }
    var channel by remember { mutableIntStateOf(1) }

    Surface(
        color = CyberColors.SurfaceContainerLowest,
        shape = RoundedCornerShape(26.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CyberColors.OutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(26.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Insignia Mando Clásico
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "MANDO CLÁSICO UNIVERSAL",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberColors.OnSurface,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "Infrarrojos / LAN",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 10.sp,
                    color = CyberColors.OnSurfaceVariant,
                )
            }

            // Fila Superior: Encendido, Entrada, Silencio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyberColors.SurfaceContainerHigh)
                        .border(1.dp, CyberColors.PowerRed.copy(alpha = 0.4f), CircleShape)
                        .clickable { onCommand(RemoteCommand.Power) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "Encender / Apagar", tint = CyberColors.PowerRed, modifier = Modifier.size(22.dp))
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyberColors.SurfaceContainerHigh)
                        .clickable { onCommand(RemoteCommand.Input) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Input, contentDescription = "Entrada", tint = CyberColors.LaserCyan, modifier = Modifier.size(20.dp))
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyberColors.SurfaceContainerHigh)
                        .clickable { onCommand(RemoteCommand.Mute) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "Silencio", tint = CyberColors.OnSurface, modifier = Modifier.size(20.dp))
                }
            }

            // Teclado Numérico Clásico Completo (1..9, 0)
            Surface(
                color = CyberColors.SurfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val rows = listOf(
                        listOf(1, 2, 3),
                        listOf(4, 5, 6),
                        listOf(7, 8, 9),
                    )

                    rows.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { digit ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberColors.SurfaceContainerHigh)
                                        .clickable { onCommand(RemoteCommand.Digit(digit)) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("$digit", fontWeight = FontWeight.Bold, color = CyberColors.OnSurface, fontSize = 15.sp)
                                }
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberColors.SurfaceContainerHigh)
                                .clickable { onCommand(RemoteCommand.Backspace) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("CLR", fontSize = 11.sp, color = CyberColors.PowerRed, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberColors.SurfaceContainerHigh)
                                .clickable { onCommand(RemoteCommand.Digit(0)) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("0", fontWeight = FontWeight.Bold, color = CyberColors.OnSurface, fontSize = 15.sp)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberColors.SurfaceContainerHigh)
                                .clickable { onCommand(RemoteCommand.Enter) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("ENT", fontSize = 11.sp, color = CyberColors.LaserCyan, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Balancines de Volumen y Canal + Menú e Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Vol
                ClassicRocker(
                    label = "VOL",
                    value = "$volume",
                    onUp = {
                        volume = (volume + 1).coerceAtMost(100)
                        onCommand(RemoteCommand.VolumeUp)
                    },
                    onDown = {
                        volume = (volume - 1).coerceAtLeast(0)
                        onCommand(RemoteCommand.VolumeDown)
                    },
                )

                // Botones Centrales: Menú e Info
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberColors.SurfaceContainerHigh)
                            .clickable { onCommand(RemoteCommand.Menu) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Menú", tint = CyberColors.OnSurface, modifier = Modifier.size(20.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberColors.SurfaceContainerHigh)
                            .clickable { onCommand(RemoteCommand.Info) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = CyberColors.OnSurface, modifier = Modifier.size(20.dp))
                    }
                }

                // CH
                ClassicRocker(
                    label = "CH",
                    value = "$channel",
                    onUp = {
                        channel += 1
                        onCommand(RemoteCommand.ChannelUp)
                    },
                    onDown = {
                        if (channel > 1) channel -= 1
                        onCommand(RemoteCommand.ChannelDown)
                    },
                )
            }

            // Rueda D-Pad con OK Central
            ClassicCircularDpad(onCommand = onCommand)

            // Fila de Atrás y Home
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberColors.SurfaceContainerHigh)
                        .clickable { onCommand(RemoteCommand.Back) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = CyberColors.OnSurface, modifier = Modifier.size(20.dp))
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberColors.SurfaceContainerHigh)
                        .clickable { onCommand(RemoteCommand.Home) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Home, contentDescription = "Inicio", tint = CyberColors.LaserCyan, modifier = Modifier.size(20.dp))
                }
            }

            // 4 Botones de Color (Rojo, Verde, Amarillo, Azul)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(CyberColors.RemoteRed).clickable { onCommand(RemoteCommand.ColorKey(KeyColor.RED)) })
                Box(modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(CyberColors.RemoteGreen).clickable { onCommand(RemoteCommand.ColorKey(KeyColor.GREEN)) })
                Box(modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(CyberColors.RemoteYellow).clickable { onCommand(RemoteCommand.ColorKey(KeyColor.YELLOW)) })
                Box(modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(CyberColors.RemoteBlue).clickable { onCommand(RemoteCommand.ColorKey(KeyColor.BLUE)) })
            }

            // Barra Multimedia Clásica (Rewind, Play, Pause, Forward)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(CyberColors.SurfaceContainerHigh).clickable { onCommand(RemoteCommand.Rewind) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.FastRewind, contentDescription = null, tint = CyberColors.OnSurface, modifier = Modifier.size(20.dp))
                }
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(CyberColors.SurfaceContainerHigh).clickable { onCommand(RemoteCommand.Play) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CyberColors.LaserCyan, modifier = Modifier.size(20.dp))
                }
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(CyberColors.SurfaceContainerHigh).clickable { onCommand(RemoteCommand.Pause) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Pause, contentDescription = null, tint = CyberColors.OnSurface, modifier = Modifier.size(20.dp))
                }
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(CyberColors.SurfaceContainerHigh).clickable { onCommand(RemoteCommand.FastForward) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = CyberColors.OnSurface, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ClassicCircularDpad(onCommand: (RemoteCommand) -> Unit) {
    Box(
        modifier = Modifier
            .size(190.dp)
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
                .size(width = 70.dp, height = 44.dp)
                .clip(RoundedCornerShape(topStart = 35.dp, topEnd = 35.dp))
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
                .size(width = 70.dp, height = 44.dp)
                .clip(RoundedCornerShape(bottomStart = 35.dp, bottomEnd = 35.dp))
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
                .size(width = 44.dp, height = 70.dp)
                .clip(RoundedCornerShape(topStart = 35.dp, bottomStart = 35.dp))
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
                .size(width = 44.dp, height = 70.dp)
                .clip(RoundedCornerShape(topEnd = 35.dp, bottomEnd = 35.dp))
                .background(CyberColors.SurfaceContainerHigh)
                .clickable { onCommand(RemoteCommand.DpadRight) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Derecha", tint = CyberColors.OnSurface, modifier = Modifier.size(26.dp))
        }

        // OK Central
        Box(
            modifier = Modifier
                .size(70.dp)
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
private fun ClassicRocker(
    label: String,
    value: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Surface(
        color = CyberColors.SurfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(66.dp)
            .border(1.dp, CyberColors.OutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberColors.SurfaceContainerHigh)
                    .clickable { onUp() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Subir $label", tint = CyberColors.OnSurface, modifier = Modifier.size(18.dp))
            }

            Column(modifier = Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberColors.OnSurface)
                Text(value, fontSize = 11.sp, color = CyberColors.OnSurfaceVariant)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberColors.SurfaceContainerHigh)
                    .clickable { onDown() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Bajar $label", tint = CyberColors.OnSurface, modifier = Modifier.size(18.dp))
            }
        }
    }
}
