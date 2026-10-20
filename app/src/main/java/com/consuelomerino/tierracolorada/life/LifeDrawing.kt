package com.consuelomerino.tierracolorada.life

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import androidx.compose.ui.graphics.drawscope.translate
import com.consuelomerino.tierracolorada.objects.GardenObject
import com.consuelomerino.tierracolorada.ui.theme.LeafLight
import kotlin.math.PI
import kotlin.math.sin

private val Hummingbird = Color(0xFF2E8B57)
private val HummingbirdThroat = Color(0xFFB0283A)
private val Beak = Color(0xFF2B2B2B)

/** Canopy shadow blobs: (x, y) as a fraction of the screen, and size as a fraction of its width. */
private val ShadowBlobs = listOf(
    Triple(0.1f, 0.15f, 0.55f),
    Triple(0.6f, 0.3f, 0.7f),
    Triple(0.3f, 0.55f, 0.6f),
    Triple(0.8f, 0.7f, 0.5f),
    Triple(0.45f, 0.9f, 0.65f),
)

/** Draws everything alive above the garden: shadows, pond ripples, leaves and the hummingbird. */
fun DrawScope.drawLife(state: LifeState, ripples: List<Ripple>, nowMs: Long, cellSize: Float, pond: GardenObject?) {
    if (state.showShadows) drawCanopyShadows(state.shadowPhase)
    pond?.let { drawPondRings(it, nowMs, cellSize, animate = state.showShadows) }
    for (ripple in ripples) {
        val t = ripple.ageMs / PondRipples.LIFETIME_MS.toFloat()
        drawCircle(
            Color.White.copy(alpha = 0.5f * (1 - t)),
            radius = (4 + t * 20) * cellSize,
            center = Offset(ripple.x * cellSize, ripple.y * cellSize),
            style = Stroke(width = cellSize * 0.8f),
        )
    }
    for (leaf in state.leaves) drawLeaf(leaf, nowMs, cellSize)
    state.hummingbird?.let { drawHummingbird(it, nowMs, cellSize) }
}

private fun DrawScope.drawCanopyShadows(phase: Float) {
    for ((x, y, s) in ShadowBlobs) {
        val radius = size.width * s / 2
        val span = size.width + radius * 2
        val cx = ((x + phase) % 1f) * span - radius
        val center = Offset(cx, y * size.height)
        drawOval(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent), center = center, radius = radius),
            topLeft = center - Offset(radius, radius * 0.6f),
            size = Size(radius * 2, radius * 1.2f),
        )
    }
}

private fun DrawScope.drawPondRings(pond: GardenObject, nowMs: Long, cellSize: Float, animate: Boolean) {
    val r = pond.radius * cellSize
    for (k in 0..1) {
        val t = if (animate) ((nowMs / 4000f) + k * 0.5f) % 1f else 0.3f + k * 0.35f
        drawCircle(
            Color.White.copy(alpha = 0.25f * (1 - t)),
            radius = r * (0.25f + 0.45f * t),
            center = Offset(pond.x * cellSize - r * 0.2f, pond.y * cellSize - r * 0.1f),
            style = Stroke(width = cellSize * 0.5f),
        )
    }
}

private fun DrawScope.drawLeaf(leaf: DriftingLeaf, nowMs: Long, cellSize: Float) {
    val age = nowMs - leaf.startMs
    val t = (age / DriftingLeaf.DRIFT_MS.toFloat()).coerceAtMost(1f)
    val alpha = if (age <= DriftingLeaf.DRIFT_MS) 1f else (1 - (age - DriftingLeaf.DRIFT_MS) / DriftingLeaf.FADE_MS.toFloat()).coerceIn(0f, 1f)
    val sway = sin(t * 3 * PI).toFloat() * 12 * (1 - t)
    val x = (leaf.fromX + (leaf.toX - leaf.fromX) * t) * cellSize
    val y = (leaf.fromY + (leaf.toY - leaf.fromY) * t + sway) * cellSize
    val length = 16 * cellSize
    val shape = Path().apply {
        moveTo(-length / 2, 0f)
        quadraticTo(0f, -length * 0.35f, length / 2, 0f)
        quadraticTo(0f, length * 0.35f, -length / 2, 0f)
        close()
    }
    translate(x, y) {
        rotateRad(leaf.seed % 7 * 0.45f + sway * 0.05f, pivot = Offset.Zero) {
            drawPath(shape, LeafLight, alpha = alpha * 0.9f)
        }
    }
}

private fun DrawScope.drawHummingbird(visit: HummingbirdVisit, nowMs: Long, cellSize: Float) {
    // Hover just beside the flower, not on top of it.
    val target = Offset(visit.target.x * cellSize + visit.target.radius * cellSize * 1.3f, visit.target.y * cellSize - visit.target.radius * cellSize * 0.5f)
    val entry = Offset(-60 * cellSize, target.y - 60 * cellSize)
    val exit = Offset(size.width + 60 * cellSize, target.y - 90 * cellSize)
    val elapsed = nowMs - visit.startMs
    val bob = Offset(0f, sin(nowMs / 180.0).toFloat() * cellSize)
    val (position, facingRight) = when (visit.phase(nowMs)) {
        VisitPhase.FLYING_IN -> lerp(entry, target, easeOut(elapsed / HummingbirdVisit.FLY_IN_MS.toFloat())) to true
        VisitPhase.HOVERING -> target + bob to false
        VisitPhase.LEAVING -> {
            val t = (elapsed - HummingbirdVisit.FLY_IN_MS - visit.hoverMs) / HummingbirdVisit.LEAVE_MS.toFloat()
            lerp(target, exit, t * t) to true
        }
        VisitPhase.DONE -> return
    }
    val s = cellSize * BIRD_SCALE
    val dir = if (facingRight) 1f else -1f
    translate(position.x, position.y) {
        // Wings flicker every frame so they read as a blur.
        val flap = sin(nowMs / 15.0).toFloat()
        for (side in listOf(-1f, 1f)) {
            drawOval(
                Hummingbird.copy(alpha = 0.3f),
                topLeft = Offset(-2.5f * s, side * (1.5f + flap) * s - 2.5f * s),
                size = Size(5f * s, 5f * s * (0.6f + 0.4f * flap * side)),
            )
        }
        drawOval(Hummingbird, topLeft = Offset(-3f * s, -1.2f * s), size = Size(6f * s, 2.4f * s))
        drawCircle(HummingbirdThroat, 0.9f * s, Offset(dir * 2.4f * s, 0.3f * s))
        drawCircle(Hummingbird, 1.1f * s, Offset(dir * 2.6f * s, -0.3f * s))
        drawLine(Beak, Offset(dir * 3.5f * s, -0.3f * s), Offset(dir * 6f * s, -0.1f * s), strokeWidth = 0.35f * s)
    }
}

private const val BIRD_SCALE = 3f

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private fun easeOut(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return 1 - (1 - c) * (1 - c)
}
