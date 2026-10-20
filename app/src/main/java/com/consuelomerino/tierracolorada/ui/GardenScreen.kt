package com.consuelomerino.tierracolorada.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.consuelomerino.tierracolorada.R
import com.consuelomerino.tierracolorada.sand.HeightMap
import com.consuelomerino.tierracolorada.sand.SandShader

/** The whole game screen: the garden canvas, its gestures, the toolbar and the clear dialog. */
@Composable
fun GardenScreen(viewModel: GardenViewModel, onEvent: (GardenEvent) -> Unit = {}) {
    val view = LocalView.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == GardenEvent.HapticTick) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onEvent(event)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("garden")) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        LaunchedEffect(widthPx, heightPx) {
            val grid = HeightMap.forScreen(widthPx, heightPx)
            viewModel.initialize(grid.width, grid.height)
        }
        val heights = viewModel.heights
        if (heights != null) {
            val cellSize = widthPx.toFloat() / heights.width
            SandCanvas(viewModel, heights, cellSize)
        }

        Toolbar(
            tool = viewModel.tool,
            muted = viewModel.muted,
            shareEnabled = false,
            onSelectTool = viewModel::selectTool,
            onClear = viewModel::requestClear,
            onToggleMute = viewModel::toggleMute,
            onShare = {},
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 16.dp),
        )
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
private fun SandCanvas(viewModel: GardenViewModel, heights: HeightMap, cellSize: Float) {
    val bitmap = remember(heights) { ImageBitmap(heights.width, heights.height) }
    val pixels = remember(heights) { IntArray(heights.width * heights.height) }
    val tapSlopPx = with(LocalDensity.current) { 10.dp.toPx() }

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(viewModel, cellSize) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val pointerId = down.id
                    val start = down.position
                    var moved = false
                    viewModel.strokeStart(start.x / cellSize, start.y / cellSize)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        // Only the first finger draws; other fingers are ignored until it lifts.
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) break
                        if ((change.position - start).getDistance() > tapSlopPx) moved = true
                        if (moved) {
                            viewModel.strokeMove(
                                change.position.x / cellSize,
                                change.position.y / cellSize,
                                change.uptimeMillis - change.previousUptimeMillis,
                            )
                        }
                        change.consume()
                    }
                    viewModel.strokeEnd()
                    if (!moved) viewModel.tap(start.x / cellSize, start.y / cellSize)
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
            dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.Low,
        )
    }
}
