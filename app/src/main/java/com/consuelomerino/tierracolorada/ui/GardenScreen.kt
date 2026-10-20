package com.consuelomerino.tierracolorada.ui

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.consuelomerino.tierracolorada.R
import com.consuelomerino.tierracolorada.life.LifeDirector
import com.consuelomerino.tierracolorada.life.LifeState
import com.consuelomerino.tierracolorada.life.drawLife
import com.consuelomerino.tierracolorada.objects.GardenObject
import com.consuelomerino.tierracolorada.objects.ObjectType
import com.consuelomerino.tierracolorada.objects.drawGardenObject
import com.consuelomerino.tierracolorada.sand.CellRect
import com.consuelomerino.tierracolorada.sand.HeightMap
import com.consuelomerino.tierracolorada.sand.SandShader
import com.consuelomerino.tierracolorada.share.GardenSharer
import kotlin.random.Random

/** The most recent frame of ambient life, kept so a shared picture can include it. */
private class LifeSnapshot {
    var state = LifeState(null, emptyList(), 0f, showShadows = false)
    var nowMs = 0L
}

/** An object being dragged: a new one from the tray ([newType]) or an existing one ([movingId]). */
private data class Drag(val newType: ObjectType? = null, val movingId: Long? = null, val finger: Offset)

/** The whole game screen: the garden canvas, its gestures, the toolbar, tray and dialogs. */
@Composable
fun GardenScreen(viewModel: GardenViewModel, onEvent: (GardenEvent) -> Unit = {}) {
    val view = LocalView.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == GardenEvent.HapticTick) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onEvent(event)
        }
    }

    var drag by remember { mutableStateOf<Drag?>(null) }
    var trashBounds by remember { mutableStateOf(Rect.Zero) }
    val lifeSnapshot = remember { LifeSnapshot() }
    val context = LocalContext.current
    val appName = stringResource(R.string.app_name)

    BoxWithConstraints(Modifier.fillMaxSize().testTag("garden")) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        LaunchedEffect(widthPx, heightPx) {
            val grid = HeightMap.forScreen(widthPx, heightPx)
            viewModel.initialize(grid.width, grid.height)
        }
        val heights = viewModel.heights ?: return@BoxWithConstraints
        val cellSize = widthPx.toFloat() / heights.width

        SandCanvas(
            viewModel = viewModel,
            heights = heights,
            cellSize = cellSize,
            onMoveStart = { id, finger -> drag = Drag(movingId = id, finger = finger) },
            onMove = { finger -> drag = drag?.copy(finger = finger) },
            onMoveEnd = { id ->
                if (drag?.finger?.let(trashBounds::contains) == true) viewModel.deleteObject(id) else viewModel.endMoveObject(id)
                drag = null
            },
        )

        LifeLayer(viewModel, heights, cellSize, lifeSnapshot)

        // Ghost of a new object being dragged from the tray.
        drag?.newType?.let { type ->
            val finger = drag!!.finger
            Canvas(Modifier.fillMaxSize()) {
                drawGardenObject(GardenObject(-1, type, finger.x / cellSize, finger.y / cellSize, seed = 3), cellSize, alpha = 0.7f)
            }
        }

        val bottomBar = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(bottom = 16.dp)

        // The tray stays composed (just hidden) while dragging, so it keeps receiving the drag.
        Column(
            bottomBar.alpha(if (drag != null) 0f else 1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (viewModel.tool == Tool.PLACE) {
                ObjectTray(
                    canAdd = viewModel::canAdd,
                    full = viewModel.gardenFull,
                    onTapAdd = { type -> viewModel.addObject(type, heights.width / 2f, heights.height / 2f) },
                    onDragStart = { type, finger -> drag = Drag(newType = type, finger = finger) },
                    onDrag = { finger -> drag = drag?.copy(finger = finger) },
                    onDragEnd = {
                        val dropped = drag
                        if (dropped?.newType != null && !trashBounds.contains(dropped.finger)) {
                            viewModel.addObject(dropped.newType, dropped.finger.x / cellSize, dropped.finger.y / cellSize)
                        }
                        drag = null
                    },
                )
            }
            Toolbar(
                tool = viewModel.tool,
                muted = viewModel.muted,
                shareEnabled = true,
                onSelectTool = viewModel::selectTool,
                onClear = viewModel::requestClear,
                onToggleMute = viewModel::toggleMute,
                onShare = {
                    val image = GardenSharer.render(
                        widthPx, heightPx, fullSandImage(heights), viewModel.objects, lifeSnapshot.state,
                        viewModel.pondRipples.active(lifeSnapshot.nowMs), lifeSnapshot.nowMs, appName,
                    )
                    GardenSharer.share(context, image)
                },
            )
        }

        if (drag != null) {
            TrashZone(
                highlighted = drag?.finger?.let(trashBounds::contains) == true,
                onBounds = { trashBounds = it },
                modifier = bottomBar,
            )
        }
    }

    if (viewModel.confirmingClear) {
        AlertDialog(
            modifier = Modifier.testTag("clear_dialog"),
            onDismissRequest = viewModel::cancelClear,
            title = { Text(stringResource(R.string.clear_title)) },
            text = { Text(stringResource(R.string.clear_message)) },
            confirmButton = { TextButton(onClick = viewModel::confirmClear) { Text(stringResource(R.string.clear_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelClear) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (viewModel.clearing) {
        LaunchedEffect(Unit) {
            val start = withFrameMillis { it }
            while (viewModel.clearing) {
                withFrameMillis { now -> viewModel.advanceClear(now - start) }
            }
        }
    }
}

@Composable
private fun SandCanvas(
    viewModel: GardenViewModel,
    heights: HeightMap,
    cellSize: Float,
    onMoveStart: (Long, Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onMoveEnd: (Long) -> Unit,
) {
    val bitmap = remember(heights) { ImageBitmap(heights.width, heights.height) }
    val pixels = remember(heights) { IntArray(heights.width * heights.height) }
    val tapSlopPx = with(LocalDensity.current) { 10.dp.toPx() }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(viewModel, cellSize) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val cell = down.position / cellSize
                    val target = if (viewModel.tool == Tool.PLACE) {
                        drawOrder(viewModel.objects).lastOrNull { it.covers(cell.x, cell.y) }
                    } else {
                        null
                    }
                    when {
                        target != null -> moveObjectGesture(viewModel, target, down, cellSize, onMoveStart, onMove, onMoveEnd)
                        viewModel.tool == Tool.PLACE -> {
                            if (!followUntilUp(down, tapSlopPx) {}) viewModel.tap(cell.x, cell.y)
                        }
                        else -> {
                            viewModel.strokeStart(cell.x, cell.y)
                            val moved = followUntilUp(down, tapSlopPx) { change ->
                                viewModel.strokeMove(
                                    change.position.x / cellSize,
                                    change.position.y / cellSize,
                                    change.uptimeMillis - change.previousUptimeMillis,
                                )
                            }
                            viewModel.strokeEnd()
                            if (!moved) viewModel.tap(cell.x, cell.y)
                        }
                    }
                }
            },
    ) {
        // Reading sandFrame here redraws the canvas whenever the sand changes.
        viewModel.sandFrame
        heights.takeDirty()?.let { rect ->
            SandShader.shade(heights, rect, pixels)
            bitmap.asAndroidBitmap().setPixels(
                pixels, rect.top * heights.width + rect.left, heights.width,
                rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top,
            )
        }
        drawImage(
            bitmap,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Low,
        )
        for (obj in drawOrder(viewModel.objects)) drawGardenObject(obj, cellSize)
    }
}

/** Hummingbird, leaves, shadows and ripples, animated only while the app is visible. */
@Composable
private fun LifeLayer(viewModel: GardenViewModel, heights: HeightMap, cellSize: Float, snapshot: LifeSnapshot) {
    val context = LocalContext.current
    val reducedMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val director = remember(heights) { LifeDirector(Random.Default, heights.width, heights.height) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) withFrameMillis { now = System.currentTimeMillis() }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val objects = viewModel.objects
        val state = director.update(now, objects, reducedMotion)
        snapshot.state = state
        snapshot.nowMs = now
        drawLife(state, viewModel.pondRipples.active(now), now, cellSize, objects.firstOrNull { it.type == ObjectType.POND })
    }
}

/** Shades the whole sand surface into a fresh image (for sharing). */
private fun fullSandImage(heights: HeightMap): ImageBitmap {
    val pixels = IntArray(heights.width * heights.height)
    SandShader.shade(heights, CellRect(0, 0, heights.width, heights.height), pixels)
    return ImageBitmap(heights.width, heights.height).also {
        it.asAndroidBitmap().setPixels(pixels, 0, heights.width, 0, 0, heights.width, heights.height)
    }
}

/** The pond sits under everything else. */
private fun drawOrder(objects: List<GardenObject>) = objects.sortedBy { it.type != ObjectType.POND }

/**
 * Follows only the first finger until it lifts; other fingers are ignored.
 * Calls [onDrag] once the finger has moved beyond [slop]. Returns whether it moved.
 */
private suspend fun AwaitPointerEventScope.followUntilUp(
    down: PointerInputChange,
    slop: Float,
    onDrag: (PointerInputChange) -> Unit,
): Boolean {
    var moved = false
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        if ((change.position - down.position).getDistance() > slop) moved = true
        if (moved) onDrag(change)
        change.consume()
    }
    return moved
}

/** Drag with one finger to move an object; pinch with two to resize and rotate it. */
private suspend fun AwaitPointerEventScope.moveObjectGesture(
    viewModel: GardenViewModel,
    target: GardenObject,
    down: PointerInputChange,
    cellSize: Float,
    onMoveStart: (Long, Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onMoveEnd: (Long) -> Unit,
) {
    val grab = Offset(target.x, target.y) - down.position / cellSize
    var zoom = 1f
    var rotationDegrees = 0f
    onMoveStart(target.id, down.position)
    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.isEmpty()) break
        if (pressed.size >= 2) {
            zoom *= event.calculateZoom()
            rotationDegrees += event.calculateRotation()
            viewModel.transformObject(target.id, target.scale * zoom, target.rotation + Math.toRadians(rotationDegrees.toDouble()).toFloat())
        } else {
            val finger = pressed.first().position
            val cell = finger / cellSize + grab
            viewModel.moveObject(target.id, cell.x, cell.y)
            onMove(finger)
        }
        event.changes.forEach { it.consume() }
    }
    onMoveEnd(target.id)
}
