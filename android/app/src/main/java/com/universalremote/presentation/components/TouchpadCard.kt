package com.universalremote.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.universalremote.domain.model.RemoteCommand
import com.universalremote.presentation.theme.CyberColors

/** Superficie táctil: arrastrar mueve el puntero (o el D-pad), tocar hace clic. */
@Composable
fun TouchpadCard(onCommand: (RemoteCommand) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(CyberColors.SurfaceContainerLow, shape)
            .border(1.dp, CyberColors.LaserCyan.copy(alpha = 0.4f), shape)
            .pointerInput(Unit) { detectTapGestures(onTap = { onCommand(RemoteCommand.TouchTap) }) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onCommand(RemoteCommand.TouchDelta(drag.x, drag.y))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text("Desliza para mover · toca para seleccionar", color = CyberColors.OnSurfaceVariant, fontSize = 12.sp)
    }
}
