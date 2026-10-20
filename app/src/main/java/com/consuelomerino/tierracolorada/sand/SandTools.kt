package com.consuelomerino.tierracolorada.sand

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot

/** Brush-style sand tools that work on a circular area of the [HeightMap]. */
object SandTools {
    const val SMOOTH_RADIUS = 12f
    const val SMOOTH_FACTOR = 0.2f
    const val RIPPLE_COUNT = 3
    const val RIPPLE_GAP = 3f
    const val RIPPLE_DEPTH = -0.6f

    /** Moves every height within [SMOOTH_RADIUS] of the centre part of the way back to flat. */
    fun smooth(map: HeightMap, cx: Float, cy: Float, isBlocked: (Int, Int) -> Boolean = { _, _ -> false }) {
        forCellsWithin(map, cx, cy, SMOOTH_RADIUS) { x, y, d ->
            if (d <= SMOOTH_RADIUS && !isBlocked(x, y)) map[x, y] = map[x, y] * (1 - SMOOTH_FACTOR)
        }
    }

    /** Presses concentric rings into the sand around an object, like a stone set into a raked garden. */
    fun stampRipples(
        map: HeightMap,
        cx: Float,
        cy: Float,
        objectRadius: Float,
        isBlocked: (Int, Int) -> Boolean = { _, _ -> false },
    ) {
        val outer = objectRadius + RIPPLE_GAP * RIPPLE_COUNT + 1
        forCellsWithin(map, cx, cy, outer) { x, y, d ->
            if (isBlocked(x, y)) return@forCellsWithin
            for (k in 1..RIPPLE_COUNT) {
                val fromRing = abs(d - (objectRadius + RIPPLE_GAP * k))
                if (fromRing <= 1f) {
                    map[x, y] = RIPPLE_DEPTH * cos((PI * fromRing / 2).toFloat())
                    break
                }
            }
        }
    }

    private inline fun forCellsWithin(map: HeightMap, cx: Float, cy: Float, radius: Float, action: (Int, Int, Float) -> Unit) {
        for (y in floor(cy - radius).toInt()..ceil(cy + radius).toInt()) {
            for (x in floor(cx - radius).toInt()..ceil(cx + radius).toInt()) {
                if (map.inBounds(x, y)) action(x, y, hypot(x - cx, y - cy))
            }
        }
    }
}
