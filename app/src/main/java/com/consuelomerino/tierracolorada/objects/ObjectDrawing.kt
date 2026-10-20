package com.consuelomerino.tierracolorada.objects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import com.consuelomerino.tierracolorada.ui.theme.HeliconiaRed
import com.consuelomerino.tierracolorada.ui.theme.HeliconiaYellow
import com.consuelomerino.tierracolorada.ui.theme.LeafDark
import com.consuelomerino.tierracolorada.ui.theme.LeafLight
import com.consuelomerino.tierracolorada.ui.theme.PondTeal
import kotlin.math.cos
import kotlin.math.sin

private val StoneLight = Color(0xFF8A8F87)
private val StoneDark = Color(0xFF4E524C)
private val PondRim = Color(0xFF3E8A8C)
private val Shadow = Color(0x40000000)

/** Draws [obj] at its garden position; [cellSize] converts grid cells to pixels. */
fun DrawScope.drawGardenObject(obj: GardenObject, cellSize: Float, alpha: Float = 1f) {
    val r = obj.radius * cellSize
    translate(obj.x * cellSize, obj.y * cellSize) {
        rotateRad(obj.rotation, pivot = Offset.Zero) {
            when (obj.type) {
                ObjectType.STONE -> drawStone(obj.seed, r, alpha)
                ObjectType.FERN -> drawFern(obj.seed, r, alpha)
                ObjectType.HELICONIA -> drawHeliconia(r, alpha)
                ObjectType.POND -> drawPond(obj.seed, r, alpha)
            }
        }
    }
}

/** A closed, rounded path through the points, using midpoints as curve anchors. */
private fun smoothOutline(points: List<Pair<Float, Float>>): Path = Path().apply {
    val n = points.size
    fun mid(i: Int): Offset {
        val (ax, ay) = points[i % n]
        val (bx, by) = points[(i + 1) % n]
        return Offset((ax + bx) / 2, (ay + by) / 2)
    }
    val start = mid(0)
    moveTo(start.x, start.y)
    for (i in 1..n) {
        val (cx, cy) = points[i % n]
        val end = mid(i)
        quadraticTo(cx, cy, end.x, end.y)
    }
    close()
}

private fun DrawScope.drawStone(seed: Int, r: Float, alpha: Float) {
    val outline = smoothOutline(ObjectShapes.stoneOutline(seed, r))
    translate(r * 0.12f, r * 0.15f) { drawPath(outline, Shadow, alpha = alpha) }
    drawPath(
        outline,
        Brush.radialGradient(listOf(StoneLight, StoneDark), center = Offset(-0.3f * r, -0.3f * r), radius = 1.3f * r),
        alpha = alpha,
    )
    if (seed % 3 == 0) drawCircle(LeafLight.copy(alpha = 0.4f * alpha), r * 0.35f, Offset(0.2f * r, -0.35f * r))
}

private fun DrawScope.drawFern(seed: Int, r: Float, alpha: Float) {
    drawCircle(Shadow, r * 0.55f, Offset(r * 0.1f, r * 0.12f), alpha = alpha * 0.6f)
    ObjectShapes.fernFronds(seed).forEachIndexed { index, angle ->
        val color = if (index % 2 == 0) LeafDark else LeafLight
        val dir = Offset(cos(angle), sin(angle))
        val perp = Offset(-dir.y, dir.x)
        drawLine(color, Offset.Zero, dir * r, strokeWidth = r * 0.05f, cap = StrokeCap.Round, alpha = alpha)
        for (i in 1..LEAFLETS) {
            val t = i.toFloat() / (LEAFLETS + 1)
            val base = dir * (r * t)
            val length = r * 0.28f * (1 - t * 0.7f)
            for (side in listOf(1f, -1f)) {
                val tip = base + (perp * side + dir * 0.4f) * length
                drawLine(color, base, tip, strokeWidth = r * 0.08f * (1 - t * 0.5f), cap = StrokeCap.Round, alpha = alpha)
            }
        }
    }
}

private fun DrawScope.drawHeliconia(r: Float, alpha: Float) {
    // Two broad leaves behind the flower stalk.
    for (side in listOf(-1f, 1f)) {
        drawOval(LeafDark, topLeft = Offset(side * r * 0.55f - r * 0.3f, -r * 0.9f), size = Size(r * 0.6f, r * 1.8f), alpha = alpha * 0.85f)
    }
    drawLine(LeafDark, Offset(0f, r), Offset(0f, -r), strokeWidth = r * 0.1f, cap = StrokeCap.Round, alpha = alpha)
    for (k in 0 until BRACTS) {
        val y = -r * 0.8f + k * r * 0.3f
        val side = if (k % 2 == 0) 1f else -1f
        val bract = Path().apply {
            moveTo(0f, y - r * 0.12f)
            lineTo(side * r * 0.5f, y - r * 0.2f)
            lineTo(0f, y + r * 0.12f)
            close()
        }
        drawPath(bract, HeliconiaRed, alpha = alpha)
        drawCircle(HeliconiaYellow, r * 0.07f, Offset(side * r * 0.45f, y - r * 0.19f), alpha = alpha)
    }
}

private fun DrawScope.drawPond(seed: Int, r: Float, alpha: Float) {
    val outline = smoothOutline(ObjectShapes.stoneOutline(seed, r, points = 16))
    drawPath(outline, PondTeal, alpha = alpha)
    drawPath(outline, PondRim, style = Stroke(width = r * 0.06f), alpha = alpha)
    val padCenter = Offset(r * 0.35f, r * 0.2f)
    drawArc(
        LeafLight, startAngle = 20f, sweepAngle = 320f, useCenter = true,
        topLeft = padCenter - Offset(r * 0.2f, r * 0.2f), size = Size(r * 0.4f, r * 0.4f), alpha = alpha,
    )
}

private const val LEAFLETS = 10
private const val BRACTS = 6
