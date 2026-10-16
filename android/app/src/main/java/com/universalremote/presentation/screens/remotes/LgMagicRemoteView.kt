package com.universalremote.presentation.screens.remotes

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.universalremote.domain.model.StreamingApp
import com.universalremote.presentation.theme.CyberColors

@Composable
fun LgMagicRemoteView(
    onCommand: (RemoteCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNumericPad by remember { mutableStateOf(false) }

    Surface(
        color = CyberColors.SurfaceContainerLowest,
        shape = RoundedCornerShape(32.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, CyberColors.OutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(32.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Insignia del LG Magic Remote
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "LG MAGIC REMOTE",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberColors.Secondary,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = "webOS",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 10.sp,
                    color = CyberColors.OnSurfaceVariant,
                )
            }

            // Fila Superior: Encendido, Entrada y Toggle Teclado Numérico
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Power
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyberColors.SurfaceContainerHigh)
                        .clickable { onCommand(RemoteCommand.Power) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PowerSettingsNew, contentDescription = "Encender / Apagar", tint = CyberColors.PowerRed, modifier = Modifier.size(22.dp))
                }

                // Entrada
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyberColors.SurfaceContainerHigh)
                        .clickable { onCommand(RemoteCommand.Input) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Input, contentDescription = "Entrada", tint = CyberColors.OnSurface, modifier = Modifier.size(20.dp))
                }

                // Toggle Numpad (123)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (showNumericPad) CyberColors.Secondary.copy(alpha = 0.2f) else CyberColors.SurfaceContainerHigh)
                        .border(1.dp, if (showNumericPad) CyberColors.Secondary else Color.Transparent, CircleShape)
                        .clickable { showNumericPad = !showNumericPad },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Dialpad, contentDescription = "Números", tint = CyberColors.Secondary, modifier = Modifier.size(20.dp))
                }
            }

            // Teclado numérico desplegable LG
            AnimatedVisibility(visible = showNumericPad) {
                Surface(
                    color = CyberColors.SurfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            listOf(1, 2, 3),
                            listOf(4, 5, 6),
                            listOf(7, 8, 9),
                        ).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { digit ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(CyberColors.SurfaceContainerHigh)
                                            .clickable { onCommand(RemoteCommand.Digit(digit)) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("$digit", fontWeight = FontWeight.Bold, color = CyberColors.OnSurface)
                                    }
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyberColors.SurfaceContainerHigh)
                                    .clickable { onCommand(RemoteCommand.Backspace) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("Borrar", fontSize = 11.sp, color = CyberColors.PowerRed)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyberColors.SurfaceContainerHigh)
                                    .clickable { onCommand(RemoteCommand.Digit(0)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("0", fontWeight = FontWeight.Bold, color = CyberColors.OnSurface)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyberColors.SurfaceContainerHigh)
                                    .clickable { onCommand(RemoteCommand.Enter) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("OK", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberColors.Secondary)
                            }
                        }
                    }
                }
            }

            // Sección Volumen, Silencio y Canal LG Magic
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Columna Volumen
                LgRockerCapsule(
                    label = "VOL",
                    value = "",
                    onUp = {
                        onCommand(RemoteCommand.VolumeUp)
                    },
                    onDown = {
                        onCommand(RemoteCommand.VolumeDown)
                    },
                )

                // Botón central de Silencio (MUTE) de LG
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(CyberColors.SurfaceContainerHigh)
                        .border(1.dp, CyberColors.OutlineVariant.copy(alpha = 0.3f), CircleShape)
                        .clickable { onCommand(RemoteCommand.Mute) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "Silencio", tint = CyberColors.OnSurface, modifier = Modifier.size(22.dp))
                }

                // Columna Canal
                LgRockerCapsule(
                    label = "CH",
                    value = "",
                    onUp = {
                        onCommand(RemoteCommand.ChannelUp)
                    },
                    onDown = {
                        onCommand(RemoteCommand.ChannelDown)
                    },
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Rueda Mágica Central (LG Magic Wheel OK con D-Pad alrededor)
            LgMagicWheelAssembly(onCommand = onCommand)

            Spacer(modifier = Modifier.height(2.dp))

            // Satélites LG: Atrás, Home (webOS), Micrófono, Ajustes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LgSatelliteKey(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") { onCommand(RemoteCommand.Back) }
                LgSatelliteKey(Icons.Default.Home, "Home", tint = CyberColors.Secondary) { onCommand(RemoteCommand.Home) }
                LgSatelliteKey(Icons.Default.Mic, "Voz") { onCommand(RemoteCommand.Search) }
                LgSatelliteKey(Icons.Default.Settings, "Ajustes") { onCommand(RemoteCommand.Menu) }
            }

            // Botones de colores LG (Rojo, Verde, Amarillo, Azul)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape).background(CyberColors.RemoteRed).clickable { onCommand(RemoteCommand.ColorKey(KeyColor.RED)) })
                Box(modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape).background(CyberColors.RemoteGreen).clickable { onCommand(RemoteCommand.ColorKey(KeyColor.GREEN)) })
                Box(modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape).background(CyberColors.RemoteYellow).clickable { onCommand(RemoteCommand.ColorKey(KeyColor.YELLOW)) })
                Box(modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape).background(CyberColors.RemoteBlue).clickable { onCommand(RemoteCommand.ColorKey(KeyColor.BLUE)) })
            }

            // Botones de streaming LG (Netflix, Prime, Disney+, LG Channels)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LgAppButton("NETFLIX", CyberColors.NetflixRed, Modifier.weight(1f)) { onCommand(RemoteCommand.LaunchApp(StreamingApp.NETFLIX)) }
                LgAppButton("PRIME", CyberColors.PrimeBlue, Modifier.weight(1f)) { onCommand(RemoteCommand.LaunchApp(StreamingApp.PRIME_VIDEO)) }
                LgAppButton("DISNEY+", CyberColors.DisneyBlue, Modifier.weight(1f)) { onCommand(RemoteCommand.LaunchApp(StreamingApp.DISNEY_PLUS)) }
                LgAppButton("CHANNELS", CyberColors.Secondary, Modifier.weight(1f)) { onCommand(RemoteCommand.LaunchApp(StreamingApp.LG_CHANNELS)) }
            }
        }
    }
}

@Composable
private fun LgMagicWheelAssembly(onCommand: (RemoteCommand) -> Unit) {
    Box(
        modifier = Modifier
            .size(195.dp)
            .clip(CircleShape)
            .background(CyberColors.SurfaceContainerLowest)
            .border(1.dp, CyberColors.OutlineVariant.copy(alpha = 0.35f), CircleShape)
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

        // Rueda Mágica Central (LG Magic Wheel)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(CyberColors.SurfaceContainerHighest)
                .border(2.dp, CyberColors.Secondary.copy(alpha = 0.5f), CircleShape)
                .clickable { onCommand(RemoteCommand.DpadCenter) },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Radio, contentDescription = null, tint = CyberColors.Secondary, modifier = Modifier.size(16.dp))
                Text(
                    text = "WHEEL",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberColors.Secondary,
                )
            }
        }
    }
}

@Composable
private fun LgRockerCapsule(
    label: String,
    value: String,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Surface(
        color = CyberColors.SurfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(68.dp)
            .border(1.dp, CyberColors.OutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(CyberColors.SurfaceContainerHigh)
                    .clickable { onUp() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Subir $label", tint = CyberColors.OnSurface, modifier = Modifier.size(18.dp))
            }

            Column(modifier = Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberColors.Secondary)
                if (value.isNotEmpty()) Text(value, fontSize = 12.sp, color = CyberColors.OnSurfaceVariant)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
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
private fun LgSatelliteKey(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color = CyberColors.OnSurface,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(CyberColors.SurfaceContainerHigh)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun LgAppButton(
    name: String,
    brandColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = CyberColors.SurfaceContainerLow,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(38.dp)
            .border(1.dp, brandColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .clickable { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name,
                fontFamily = FontFamily.SansSerif,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = brandColor,
            )
        }
    }
}
