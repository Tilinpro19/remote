package com.universalremote.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalremote.domain.model.KeyColor
import com.universalremote.domain.model.Favorite
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.domain.model.StreamingApp
import com.universalremote.presentation.RemoteUiState
import com.universalremote.presentation.theme.CyberColors

@Composable
fun AppsAndChannelsScreen(
    state: RemoteUiState,
    onSendCommand: (RemoteCommand) -> Unit,
    onTuneChannel: (String) -> Unit,
    isSupported: (RemoteCommand) -> Boolean,
    favorites: List<Favorite>,
    onToggleFavorite: (label: String, target: Favorite.Target) -> Unit,
    onRemoveFavorite: (Favorite) -> Unit,
    modifier: Modifier = Modifier,
) {
    var channelBuffer by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Título y bienvenida
        Column {
            Text(
                text = "Canales y Aplicaciones",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = CyberColors.OnSurface,
            )
            Text(
                text = "Mantén pulsada una app o «Ir al canal» para guardarla en favoritos",
                style = MaterialTheme.typography.bodySmall,
                color = CyberColors.OnSurfaceVariant,
            )
        }

        // 1b. Favoritos: toque para abrir, mantener pulsado para quitar.
        if (state.activeDevice != null && favorites.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                favorites.forEach { favorite ->
                    FavoriteChip(
                        favorite = favorite,
                        onClick = {
                            when (val target = favorite.target) {
                                is Favorite.Target.App -> onSendCommand(RemoteCommand.LaunchApp(target.app))
                                is Favorite.Target.Channel -> onTuneChannel(target.number)
                            }
                        },
                        onLongClick = { onRemoveFavorite(favorite) },
                    )
                }
            }
        }

        // 2. Botones de color de Smart TV (Rojo, Verde, Amarillo, Azul)
        if (isSupported(RemoteCommand.ColorKey(KeyColor.RED))) Surface(
            color = CyberColors.SurfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Botones de color del televisor",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = CyberColors.OnSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ColorKeyButton("Rojo", CyberColors.RemoteRed, Modifier.weight(1f)) {
                        onSendCommand(RemoteCommand.ColorKey(KeyColor.RED))
                    }
                    ColorKeyButton("Verde", CyberColors.RemoteGreen, Modifier.weight(1f)) {
                        onSendCommand(RemoteCommand.ColorKey(KeyColor.GREEN))
                    }
                    ColorKeyButton("Amarillo", CyberColors.RemoteYellow, Modifier.weight(1f)) {
                        onSendCommand(RemoteCommand.ColorKey(KeyColor.YELLOW))
                    }
                    ColorKeyButton("Azul", CyberColors.RemoteBlue, Modifier.weight(1f)) {
                        onSendCommand(RemoteCommand.ColorKey(KeyColor.BLUE))
                    }
                }
            }
        }

        // 3. Galería de Aplicaciones de Streaming en Tarjetas Visuales
        Text(
            text = "Aplicaciones de Streaming",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = CyberColors.OnSurface,
        )

        // Sólo las apps que el TV activo sabe abrir; se reagrupan de dos en dos.
        val appRows = listOf(
            AppCardItem(StreamingApp.NETFLIX, "Netflix", "Películas y Series", CyberColors.NetflixRed, null, "N") { onSendCommand(RemoteCommand.LaunchApp(StreamingApp.NETFLIX)) },
            AppCardItem(StreamingApp.YOUTUBE, "YouTube", "Vídeos y Música", CyberColors.YouTubeRed, Icons.Default.SmartDisplay) { onSendCommand(RemoteCommand.LaunchApp(StreamingApp.YOUTUBE)) },
            AppCardItem(StreamingApp.PRIME_VIDEO, "Prime Video", "Amazon Prime", CyberColors.PrimeBlue, Icons.Default.Movie) { onSendCommand(RemoteCommand.LaunchApp(StreamingApp.PRIME_VIDEO)) },
            AppCardItem(StreamingApp.DISNEY_PLUS, "Disney+", "Disney, Pixar, Marvel", CyberColors.DisneyBlue, null, "D+") { onSendCommand(RemoteCommand.LaunchApp(StreamingApp.DISNEY_PLUS)) },
            AppCardItem(StreamingApp.SPOTIFY, "Spotify", "Música y Podcasts", CyberColors.SpotifyGreen, Icons.Default.Vibration) { onSendCommand(RemoteCommand.LaunchApp(StreamingApp.SPOTIFY)) },
            AppCardItem(StreamingApp.BROWSER, "Navegador", "Internet en tu TV", CyberColors.LaserCyan, Icons.Default.Language) { onSendCommand(RemoteCommand.LaunchApp(StreamingApp.BROWSER)) },
        ).filter { isSupported(RemoteCommand.LaunchApp(it.app)) }.chunked(2)

        appRows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { item ->
                    AppLaunchCard(
                        item = item,
                        onLongClick = { onToggleFavorite(item.title, Favorite.Target.App(item.app)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }

        // 4. Sintonizador de Canales Numérico
        Surface(
            color = CyberColors.SurfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Sintonizar Canal",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = CyberColors.OnSurface,
                    )
                    Text(
                        text = if (channelBuffer.isEmpty()) "Canal: —" else "Canal: $channelBuffer",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberColors.LaserCyan,
                    )
                }

                val numpadRows = listOf(
                    listOf(1, 2, 3),
                    listOf(4, 5, 6),
                    listOf(7, 8, 9),
                )

                numpadRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { digit ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyberColors.SurfaceContainerHigh)
                                    .clickable {
                                        if (channelBuffer.length < 4) channelBuffer += digit
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "$digit",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberColors.OnSurface,
                                )
                            }
                        }
                    }
                }

                // Fila 0, Borrar, Enviar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberColors.SurfaceContainerHigh)
                            .clickable {
                                channelBuffer = channelBuffer.dropLast(1)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Borrar", color = CyberColors.PowerRed, fontWeight = FontWeight.SemiBold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberColors.SurfaceContainerHigh)
                            .clickable {
                                if (channelBuffer.length < 4) channelBuffer += "0"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("0", fontWeight = FontWeight.Bold, color = CyberColors.OnSurface, fontSize = 16.sp)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberColors.PrimaryContainer)
                            .combinedClickable(
                                onClick = {
                                    onTuneChannel(channelBuffer)
                                    channelBuffer = ""
                                },
                                onLongClick = {
                                    if (channelBuffer.isNotEmpty()) {
                                        onToggleFavorite("Canal $channelBuffer", Favorite.Target.Channel(channelBuffer))
                                        channelBuffer = ""
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Ir al canal", color = CyberColors.OnPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Búsqueda directa por texto
        Surface(
            color = CyberColors.SurfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Búsqueda por texto en pantalla",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = CyberColors.OnSurface,
                )
                Text(
                    text = "¿Estás buscando una película en Netflix o un vídeo en YouTube? Escribe aquí:",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberColors.OnSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        placeholder = { Text("Nombre de la película o serie...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberColors.LaserCyan,
                            unfocusedBorderColor = CyberColors.OutlineVariant,
                        ),
                    )

                    Button(
                        onClick = {
                            if (searchInput.isNotBlank()) {
                                onSendCommand(RemoteCommand.Text(searchInput))
                                searchInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberColors.PrimaryContainer),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Enviar", fontWeight = FontWeight.Bold, color = CyberColors.OnPrimaryContainer)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private class AppCardItem(
    val app: StreamingApp,
    val title: String,
    val subtitle: String,
    val brandColor: Color,
    val icon: ImageVector? = null,
    val letter: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun AppLaunchCard(
    item: AppCardItem,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = CyberColors.SurfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(72.dp)
            .border(1.dp, item.brandColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .combinedClickable(onClick = item.onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(item.brandColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.icon != null) {
                    Icon(imageVector = item.icon, contentDescription = item.title, tint = item.brandColor, modifier = Modifier.size(22.dp))
                } else {
                    Text(text = item.letter ?: item.title.take(1), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = item.brandColor)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberColors.OnSurface,
                )
                Text(
                    text = item.subtitle,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 10.sp,
                    color = CyberColors.OnSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ColorKeyButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .height(40.dp)
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

@Composable
private fun FavoriteChip(favorite: Favorite, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CyberColors.SurfaceContainerHigh)
            .border(1.dp, CyberColors.LaserCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("★ ${favorite.label}", color = CyberColors.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
