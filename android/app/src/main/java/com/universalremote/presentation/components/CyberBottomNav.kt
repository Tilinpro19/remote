package com.universalremote.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalremote.presentation.theme.CyberColors

enum class CyberTab(
    val title: String,
    val icon: ImageVector,
) {
    REMOTE("Mando", Icons.Default.SportsEsports),
    APPS("Canales & Apps", Icons.Default.SmartDisplay),
    MACROS("Macros", Icons.AutoMirrored.Filled.PlaylistPlay),
    DEVICES("Dispositivos", Icons.Default.Tv),
    SETTINGS("Ajustes", Icons.Default.Settings),
}

@Composable
fun CyberBottomNav(
    selectedTab: CyberTab,
    onTabSelected: (CyberTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = CyberColors.SurfaceContainerLowest.copy(alpha = 0.95f),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CyberTab.entries.forEach { tab ->
                val isSelected = tab == selectedTab
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) CyberColors.SurfaceContainer.copy(alpha = 0.9f)
                            else Color.Transparent
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onTabSelected(tab) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) CyberColors.LaserCyan else CyberColors.OnSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                        Text(
                            text = tab.title,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) CyberColors.LaserCyan else CyberColors.OnSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
