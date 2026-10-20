package com.universalremote.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalremote.domain.model.ConnectionState
import com.universalremote.domain.model.DeviceKind
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.presentation.RemoteUiState
import com.universalremote.presentation.components.TouchpadCard
import com.universalremote.presentation.screens.remotes.ClassicIrRemoteView
import com.universalremote.presentation.screens.remotes.GoogleTvRemoteView
import com.universalremote.presentation.screens.remotes.LgMagicRemoteView
import com.universalremote.presentation.screens.remotes.RokuRemoteView
import com.universalremote.presentation.screens.remotes.SamsungRemoteView
import com.universalremote.presentation.theme.CyberColors

enum class RemoteSkin(val displayName: String, val brand: String) {
    AUTO("Automático", "Detectar"),
    SAMSUNG("Samsung One", "Tizen"),
    LG("LG Magic", "webOS"),
    ROKU("Roku Voice", "Roku OS"),
    GOOGLE_TV("Google TV", "Android TV"),
    CLASSIC("Clásico IR", "Universal"),
}

@Composable
fun RemoteProScreen(
    state: RemoteUiState,
    onSendCommand: (RemoteCommand) -> Unit,
    onNavigateToDevices: () -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onOpenKeyboard: () -> Unit,
    isSupported: (RemoteCommand) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var showTouchpad by remember { mutableStateOf(false) }
    var selectedSkin by remember { mutableStateOf(RemoteSkin.AUTO) }
    var showSleepTimer by remember { mutableStateOf(false) }
    // Vive en el ViewModel: sobrevive al cambio de pestaña y realmente apaga el TV al terminar.
    val activeTimerMinutes = state.sleepTimerMinutes

    // Determinar la apariencia efectiva (Automática según la marca conectada o forzada por el usuario)
    val effectiveSkin = remember(selectedSkin, state.activeDevice) {
        if (selectedSkin != RemoteSkin.AUTO) {
            selectedSkin
        } else {
            when (state.activeDevice?.kind) {
                DeviceKind.SAMSUNG_TIZEN -> RemoteSkin.SAMSUNG
                DeviceKind.LG_WEBOS -> RemoteSkin.LG
                DeviceKind.ROKU -> RemoteSkin.ROKU
                DeviceKind.ANDROID_TV, DeviceKind.SONY_BRAVIA -> RemoteSkin.GOOGLE_TV
                // Marcas sin skin propia usan el mando clásico completo (genérico).
                DeviceKind.VIZIO -> RemoteSkin.GOOGLE_TV
                DeviceKind.INFRARED, DeviceKind.BLUETOOTH_HID, null -> RemoteSkin.CLASSIC
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. Selector rápido de televisores / Habitaciones
        RoomsCardHeader(
            state = state,
            onCardClick = onNavigateToDevices,
            onPowerClick = { onSendCommand(RemoteCommand.Power) },
        )

        // 2. Barra de acciones universales (Entrada, Búsqueda, Temporizador de Apagado, Menú)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UtilityQuickButton("Entrada", Icons.AutoMirrored.Filled.Input, CyberColors.LaserCyan, Modifier.weight(1f)) {
                onSendCommand(RemoteCommand.Input)
            }
            UtilityQuickButton("Buscar", Icons.Default.Search, CyberColors.OnSurface, Modifier.weight(1f)) {
                onSendCommand(RemoteCommand.Search)
            }
            UtilityQuickButton(
                label = if (activeTimerMinutes != null) "${activeTimerMinutes}m" else "Dormir",
                icon = Icons.Default.Timer,
                iconTint = if (activeTimerMinutes != null) CyberColors.Tertiary else CyberColors.OnSurfaceVariant,
                modifier = Modifier.weight(1f),
            ) {
                showSleepTimer = !showSleepTimer
            }
            UtilityQuickButton("Menú", Icons.Default.Settings, CyberColors.Secondary, Modifier.weight(1f)) {
                onSendCommand(RemoteCommand.Menu)
            }
        }

        // Teclado y touchpad: sólo si el TV activo los admite.
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UtilityQuickButton("Teclado", Icons.Default.Keyboard, CyberColors.LaserCyan, Modifier.weight(1f)) {
                if (isSupported(RemoteCommand.Text("a"))) onOpenKeyboard() else onSendCommand(RemoteCommand.Text("a"))
            }
            UtilityQuickButton(
                "Touchpad", Icons.Default.TouchApp,
                if (showTouchpad) CyberColors.Tertiary else CyberColors.OnSurface, Modifier.weight(1f),
            ) { showTouchpad = !showTouchpad }
            UtilityQuickButton("Borrar", Icons.Default.Backspace, CyberColors.OnSurfaceVariant, Modifier.weight(1f)) {
                onSendCommand(RemoteCommand.Backspace)
            }
            UtilityQuickButton("Enter", Icons.AutoMirrored.Filled.KeyboardReturn, CyberColors.OnSurfaceVariant, Modifier.weight(1f)) {
                onSendCommand(RemoteCommand.Enter)
            }
        }
        AnimatedVisibility(visible = showTouchpad) { TouchpadCard(onCommand = onSendCommand) }

        // Desplegable de Temporizador de Apagado
        AnimatedVisibility(visible = showSleepTimer) {
            Surface(
                color = CyberColors.SurfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Temporizador de Apagado para Dormir",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = CyberColors.OnSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(15, 30, 60).forEach { mins ->
                            val isSelected = activeTimerMinutes == mins
                            Button(
                                onClick = {
                                    if (isSelected) onCancelSleepTimer() else onStartSleepTimer(mins)
                                    showSleepTimer = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) CyberColors.TertiaryContainer else CyberColors.SurfaceContainerHigh,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "$mins min",
                                    color = if (isSelected) CyberColors.OnTertiaryContainer else CyberColors.OnSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Selector de Apariencia de Mando (Replicando Samsung, LG, Roku, Google TV, Clásico)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteSkin.entries.forEach { skin ->
                val isSelected = selectedSkin == skin
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) CyberColors.LaserCyan.copy(alpha = 0.2f)
                            else CyberColors.SurfaceContainerLow
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) CyberColors.LaserCyan else Color.Transparent,
                            shape = RoundedCornerShape(20.dp),
                        )
                        .clickable { selectedSkin = skin }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (skin == RemoteSkin.AUTO) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (isSelected) CyberColors.LaserCyan else CyberColors.OnSurfaceVariant, modifier = Modifier.size(12.dp))
                        }
                        Text(
                            text = skin.displayName,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CyberColors.LaserCyan else CyberColors.OnSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 4. Renderizar el Mando Seleccionado (Samsung, LG, Roku, Google TV o Clásico)
        Crossfade(targetState = effectiveSkin, label = "remoteSkinTransition") { skin ->
            when (skin) {
                RemoteSkin.SAMSUNG -> SamsungRemoteView(onCommand = onSendCommand)
                RemoteSkin.LG -> LgMagicRemoteView(onCommand = onSendCommand)
                RemoteSkin.ROKU -> RokuRemoteView(onCommand = onSendCommand)
                RemoteSkin.GOOGLE_TV -> GoogleTvRemoteView(onCommand = onSendCommand)
                RemoteSkin.CLASSIC, RemoteSkin.AUTO -> ClassicIrRemoteView(onCommand = onSendCommand)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun RoomsCardHeader(
    state: RemoteUiState,
    onCardClick: () -> Unit,
    onPowerClick: () -> Unit,
) {
    val active = state.activeDevice
    val isConnected = state.connection is ConnectionState.Connected

    Surface(
        color = CyberColors.SurfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberColors.OutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
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
                modifier = Modifier
                    .weight(1f)
                    .clickable { onCardClick() },
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberColors.SurfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = if (isConnected) CyberColors.LaserCyan else CyberColors.OnSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = active?.name ?: "Elegir televisor",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CyberColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isConnected) "● En línea • Toca para cambiar" else "Toca para conectar un TV",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        color = if (isConnected) CyberColors.Tertiary else CyberColors.OnSurfaceVariant,
                    )
                }
            }

            // Botón de encendido prioritario con resplandor
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberColors.SurfaceContainerHigh)
                    .border(1.dp, CyberColors.PowerRed.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable { onPowerClick() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = "Encender / Apagar",
                    tint = CyberColors.PowerRed,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun UtilityQuickButton(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = CyberColors.SurfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .height(48.dp)
            .clickable { onClick() },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                fontFamily = FontFamily.SansSerif,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = CyberColors.OnSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
