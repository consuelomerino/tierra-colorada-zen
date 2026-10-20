package com.consuelomerino.tierracolorada.sand

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Carves parallel grooves into a [HeightMap] as a finger drags across it.
 * Coordinates are in grid cells.
 */
class Rake(
    private val map: HeightMap,
    private val isBlocked: (Int, Int) -> Boolean = { _, _ -> false },
) {
    private val recent = ArrayDeque<Pair<Float, Float>>()
    private val troughThisStroke = BooleanArray(map.width * map.height)
    private var posX = 0f
    private var posY = 0f
    private var dirX = 1f
    private var dirY = 0f

    fun beginStroke(x: Float, y: Float) {
        recent.clear()
        troughThisStroke.fill(false)
        recent.addLast(x to y)
        posX = x
        posY = y
        stamp(x, y)
    }

    /** Moves the rake toward ([x], [y]) and returns how many cells it travelled. */
    fun moveTo(x: Float, y: Float): Float {
        recent.addLast(x to y)
        if (recent.size > SMOOTHING_WINDOW) recent.removeFirst()
        val targetX = recent.sumOf { it.first.toDouble() }.toFloat() / recent.size
        val targetY = recent.sumOf { it.second.toDouble() }.toFloat() / recent.size

        val dx = targetX - posX
        val dy = targetY - posY
        val length = hypot(dx, dy)
        if (length < MIN_DIRECTION_LENGTH) return 0f
        dirX = dx / length
        dirY = dy / length

        val steps = ceil(length).toInt()
        for (i in 1..steps) {
            val t = i.toFloat() / steps
            stamp(posX + dx * t, posY + dy * t)
        }
        posX = targetX
        posY = targetY
        return length
    }

    fun endStroke() {
        recent.clear()
    }

    private fun stamp(cx: Float, cy: Float) {
        val perpX = -dirY
        val perpY = dirX
        val reach = 2 * TINE_RADIUS
        for (i in 0 until TINES) {
            val offset = (i - (TINES - 1) / 2f) * TINE_SPACING
            val tx = cx + perpX * offset
            val ty = cy + perpY * offset
            for (y in floor(ty - reach).toInt()..ceil(ty + reach).toInt()) {
                for (x in floor(tx - reach).toInt()..ceil(tx + reach).toInt()) {
                    if (!map.inBounds(x, y) || isBlocked(x, y)) continue
                    val d = hypot(x - tx, y - ty)
                    val index = y * map.width + x
                    if (d <= TINE_RADIUS) {
                        val depth = GROOVE_DEPTH * cos((PI * d / (2 * TINE_RADIUS)).toFloat())
                        // Within one stroke, overlapping stamps keep the deepest groove.
                        map[x, y] = if (troughThisStroke[index]) minOf(map[x, y], depth) else depth
                        troughThisStroke[index] = true
                    } else if (d <= reach && !troughThisStroke[index]) {
                        map[x, y] = RIDGE_HEIGHT * sin((PI * (d - TINE_RADIUS) / TINE_RADIUS).toFloat())
                    }
                }
            }
        }
    }

    companion object {
        const val TINES = 4
        const val TINE_SPACING = 8f
        const val TINE_RADIUS = 2f
        const val GROOVE_DEPTH = -0.8f
        const val RIDGE_HEIGHT = 0.3f
        private const val SMOOTHING_WINDOW = 3
        private const val MIN_DIRECTION_LENGTH = 0.01f
    }
}
