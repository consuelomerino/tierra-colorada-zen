package com.consuelomerino.tierracolorada.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.consuelomerino.tierracolorada.R
import com.consuelomerino.tierracolorada.objects.GardenObject
import com.consuelomerino.tierracolorada.objects.ObjectType
import com.consuelomerino.tierracolorada.objects.drawGardenObject
import com.consuelomerino.tierracolorada.ui.theme.LeafDark

/**
 * Tray of objects shown in Place mode. Drag an item onto the garden to place it,
 * or tap it to place it in the middle (handy with a screen reader).
 * Drag positions are reported in root (screen) coordinates.
 */
@Composable
fun ObjectTray(
    canAdd: (ObjectType) -> Boolean,
    full: Boolean,
    onTapAdd: (ObjectType) -> Unit,
    onDragStart: (ObjectType, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.testTag("tray"),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xE6F7F1E8),
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (full) {
                Text(
                    stringResource(R.string.garden_full),
                    color = LeafDark,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (type in ObjectType.entries) {
                    TrayItem(type, canAdd(type), onTapAdd, onDragStart, onDrag, onDragEnd)
                }
            }
        }
    }
}

@Composable
private fun TrayItem(
    type: ObjectType,
    enabled: Boolean,
    onTapAdd: (ObjectType) -> Unit,
    onDragStart: (ObjectType, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val label = stringResource(type.labelRes())
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(56.dp)
            .testTag("tray_${type.name.lowercase()}")
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clickable(enabled = enabled) { onTapAdd(type) }
            .semantics { contentDescription = label }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { local -> onDragStart(type, origin + local) },
                    // Use the pointer's actual position: summing drag amounts double-counts the touch slop.
                    onDrag = { change, _ ->
                        change.consume()
                        onDrag(origin + change.position)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                )
            }
            .alpha(if (enabled) 1f else 0.35f),
    ) {
        Canvas(Modifier.size(44.dp)) {
            val cellSize = size.minDimension / 2 / type.baseRadius
            drawGardenObject(
                GardenObject(id = -1, type = type, x = size.width / 2 / cellSize, y = size.height / 2 / cellSize, seed = 3),
                cellSize,
            )
        }
    }
}

/** Drop target shown while dragging; reports its bounds in root coordinates. */
@Composable
fun TrashZone(highlighted: Boolean, onBounds: (Rect) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.remove)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(72.dp)
            .testTag("trash")
            .onGloballyPositioned { onBounds(it.boundsInRoot()) }
            .background(if (highlighted) Color(0xCCB3261E) else Color(0x99000000), CircleShape)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(28.dp)) {
            val w = size.width
            val stroke = Stroke(w * 0.09f)
            drawLine(Color.White, Offset(w * 0.15f, w * 0.25f), Offset(w * 0.85f, w * 0.25f), strokeWidth = w * 0.09f)
            drawLine(Color.White, Offset(w * 0.4f, w * 0.12f), Offset(w * 0.6f, w * 0.12f), strokeWidth = w * 0.09f)
            drawRect(Color.White, topLeft = Offset(w * 0.25f, w * 0.3f), size = androidx.compose.ui.geometry.Size(w * 0.5f, w * 0.6f), style = stroke)
        }
    }
}

fun ObjectType.labelRes(): Int = when (this) {
    ObjectType.STONE -> R.string.object_stone
    ObjectType.FERN -> R.string.object_fern
    ObjectType.HELICONIA -> R.string.object_heliconia
    ObjectType.POND -> R.string.object_pond
}
