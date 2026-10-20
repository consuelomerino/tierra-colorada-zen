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
    /** What this stroke has already carved into each cell: [NONE], [RIDGE] or [TROUGH]. */
    private val strokeMarks = ByteArray(map.width * map.height)
    private var startPending = false
    private var posX = 0f
    private var posY = 0f
    private var dirX = 1f
    private var dirY = 0f

    /** Puts the rake down. Nothing is carved until the finger moves and the stroke has a direction. */
    fun beginStroke(x: Float, y: Float) {
        recent.clear()
        strokeMarks.fill(NONE)
        recent.addLast(x to y)
        posX = x
        posY = y
        startPending = true
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

        if (startPending) {
            startPending = false
            stamp(posX, posY)
        }
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
                    // Within one stroke, overlapping stamps keep the deepest groove and the highest
                    // ridge, and a ridge never fills in a groove this stroke already carved.
                    if (d <= TINE_RADIUS) {
                        val depth = GROOVE_DEPTH * cos((PI * d / (2 * TINE_RADIUS)).toFloat())
                        map[x, y] = if (strokeMarks[index] == TROUGH) minOf(map[x, y], depth) else depth
                        strokeMarks[index] = TROUGH
                    } else if (d <= reach && strokeMarks[index] != TROUGH) {
                        val ridge = RIDGE_HEIGHT * sin((PI * (d - TINE_RADIUS) / TINE_RADIUS).toFloat())
                        map[x, y] = if (strokeMarks[index] == RIDGE) maxOf(map[x, y], ridge) else ridge
                        strokeMarks[index] = RIDGE
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
        private const val NONE: Byte = 0
        private const val RIDGE: Byte = 1
        private const val TROUGH: Byte = 2
    }
}
