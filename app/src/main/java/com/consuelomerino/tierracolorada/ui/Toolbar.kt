package com.consuelomerino.tierracolorada.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.consuelomerino.tierracolorada.R
import com.consuelomerino.tierracolorada.ui.theme.LeafDark

/** Floating pill of tool buttons at the bottom of the garden. */
@Composable
fun Toolbar(
    tool: Tool,
    muted: Boolean,
    shareEnabled: Boolean,
    onSelectTool: (Tool) -> Unit,
    onClear: () -> Unit,
    onToggleMute: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        color = Color(0xE6F7F1E8),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ToolButton("tool_rake", stringResource(R.string.tool_rake), tool == Tool.RAKE, { onSelectTool(Tool.RAKE) }) { drawRakeGlyph(it) }
            ToolButton("tool_place", stringResource(R.string.tool_place), tool == Tool.PLACE, { onSelectTool(Tool.PLACE) }) { drawPlaceGlyph(it) }
            ToolButton("tool_smooth", stringResource(R.string.tool_smooth), tool == Tool.SMOOTH, { onSelectTool(Tool.SMOOTH) }) { drawSmoothGlyph(it) }
            ToolButton("tool_clear", stringResource(R.string.tool_clear), null, onClear) { drawClearGlyph(it) }
            ToolButton(
                "mute",
                stringResource(if (muted) R.string.sound_on else R.string.sound_off),
                null,
                onToggleMute,
            ) { drawSpeakerGlyph(it, muted) }
            ToolButton("share", stringResource(R.string.share), null, onShare, enabled = shareEnabled) { drawShareGlyph(it) }
        }
    }
}

/** A tool the player picks ([selected] is true or false), or a one-off action ([selected] is null). */
@Composable
private fun ToolButton(
    tag: String,
    label: String,
    selected: Boolean?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    glyph: DrawScope.(Color) -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .testTag(tag)
            .background(if (selected == true) LeafDark.copy(alpha = 0.15f) else Color.Transparent, CircleShape)
            .then(
                if (selected != null) {
                    Modifier.selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
                } else {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                },
            )
            .semantics { contentDescription = label }
            .alpha(if (enabled) 1f else 0.35f),
    ) {
        Canvas(Modifier.size(24.dp)) { glyph(LeafDark) }
    }
}

private fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float) =
    drawLine(color, Offset(size.width * x1, size.height * y1), Offset(size.width * x2, size.height * y2), strokeWidth = size.width * 0.09f, cap = StrokeCap.Round)

private fun DrawScope.drawRakeGlyph(c: Color) {
    line(c, 0.15f, 0.35f, 0.85f, 0.35f)
    for (x in listOf(0.2f, 0.4f, 0.6f, 0.8f)) line(c, x, 0.35f, x, 0.6f)
    line(c, 0.5f, 0.35f, 0.5f, 0.05f)
    line(c, 0.1f, 0.85f, 0.9f, 0.85f)
}

private fun DrawScope.drawPlaceGlyph(c: Color) {
    drawCircle(c, size.width * 0.38f, style = Stroke(size.width * 0.09f))
    line(c, 0.5f, 0.3f, 0.5f, 0.7f)
    line(c, 0.3f, 0.5f, 0.7f, 0.5f)
}

private fun DrawScope.drawSmoothGlyph(c: Color) {
    val path = Path().apply {
        moveTo(size.width * 0.1f, size.height * 0.55f)
        cubicTo(size.width * 0.3f, size.height * 0.3f, size.width * 0.45f, size.height * 0.8f, size.width * 0.65f, size.height * 0.55f)
        cubicTo(size.width * 0.75f, size.height * 0.42f, size.width * 0.85f, size.height * 0.5f, size.width * 0.9f, size.height * 0.55f)
    }
    drawPath(path, c, style = Stroke(size.width * 0.09f, cap = StrokeCap.Round))
    line(c, 0.1f, 0.85f, 0.9f, 0.85f)
}

private fun DrawScope.drawClearGlyph(c: Color) {
    drawArc(c, -60f, 300f, false, topLeft = Offset(size.width * 0.15f, size.height * 0.15f),
        size = androidx.compose.ui.geometry.Size(size.width * 0.7f, size.height * 0.7f), style = Stroke(size.width * 0.09f, cap = StrokeCap.Round))
    line(c, 0.68f, 0.12f, 0.78f, 0.27f)
    line(c, 0.78f, 0.27f, 0.6f, 0.32f)
}

private fun DrawScope.drawSpeakerGlyph(c: Color, muted: Boolean) {
    val body = Path().apply {
        moveTo(size.width * 0.12f, size.height * 0.38f)
        lineTo(size.width * 0.3f, size.height * 0.38f)
        lineTo(size.width * 0.52f, size.height * 0.18f)
        lineTo(size.width * 0.52f, size.height * 0.82f)
        lineTo(size.width * 0.3f, size.height * 0.62f)
        lineTo(size.width * 0.12f, size.height * 0.62f)
        close()
    }
    drawPath(body, c)
    if (muted) {
        line(c, 0.65f, 0.38f, 0.9f, 0.62f)
        line(c, 0.9f, 0.38f, 0.65f, 0.62f)
    } else {
        drawArc(c, -45f, 90f, false, topLeft = Offset(size.width * 0.45f, size.height * 0.3f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.4f, size.height * 0.4f), style = Stroke(size.width * 0.08f, cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawShareGlyph(c: Color) {
    val a = Offset(size.width * 0.75f, size.height * 0.2f)
    val b = Offset(size.width * 0.25f, size.height * 0.5f)
    val d = Offset(size.width * 0.75f, size.height * 0.8f)
    drawLine(c, a, b, strokeWidth = size.width * 0.07f)
    drawLine(c, b, d, strokeWidth = size.width * 0.07f)
    for (p in listOf(a, b, d)) drawCircle(c, size.width * 0.12f, p)
}
