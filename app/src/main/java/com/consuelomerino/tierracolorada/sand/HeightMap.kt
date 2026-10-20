package com.consuelomerino.tierracolorada.sand

import kotlin.math.floor
import kotlin.math.roundToInt

/** A rectangle of grid cells; [right] and [bottom] are exclusive. */
data class CellRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun union(other: CellRect) = CellRect(
        minOf(left, other.left), minOf(top, other.top),
        maxOf(right, other.right), maxOf(bottom, other.bottom),
    )
}

/**
 * The sand surface: a grid of heights in [-1, 1], where 0 is flat sand,
 * negative values are grooves and positive values are ridges.
 */
class HeightMap(
    val width: Int,
    val height: Int,
    val values: FloatArray = FloatArray(width * height),
) {
    private var dirty: CellRect? = null

    fun inBounds(x: Int, y: Int) = x in 0 until width && y in 0 until height

    operator fun get(x: Int, y: Int): Float = if (inBounds(x, y)) values[y * width + x] else 0f

    operator fun set(x: Int, y: Int, value: Float) {
        if (!inBounds(x, y)) return
        values[y * width + x] = value.coerceIn(-1f, 1f)
        val cell = CellRect(x, y, x + 1, y + 1)
        dirty = dirty?.union(cell) ?: cell
    }

    /** Returns the area changed since the last call, or null if nothing changed. */
    fun takeDirty(): CellRect? = dirty.also { dirty = null }

    fun markAllDirty() {
        dirty = CellRect(0, 0, width, height)
    }

    fun resampledTo(newWidth: Int, newHeight: Int): HeightMap {
        val out = HeightMap(newWidth, newHeight)
        val sx = if (newWidth > 1) (width - 1).toFloat() / (newWidth - 1) else 0f
        val sy = if (newHeight > 1) (height - 1).toFloat() / (newHeight - 1) else 0f
        for (y in 0 until newHeight) {
            val fy = y * sy
            val y0 = floor(fy).toInt()
            val ty = fy - y0
            for (x in 0 until newWidth) {
                val fx = x * sx
                val x0 = floor(fx).toInt()
                val tx = fx - x0
                val top = this[x0, y0] * (1 - tx) + this[x0 + 1, y0] * tx
                val bottom = this[x0, y0 + 1] * (1 - tx) + this[x0 + 1, y0 + 1] * tx
                out.values[y * newWidth + x] = top * (1 - ty) + bottom * ty
            }
        }
        return out
    }

    companion object {
        const val GRID_WIDTH = 360

        fun forScreen(widthPx: Int, heightPx: Int): HeightMap {
            val h = (GRID_WIDTH.toFloat() * heightPx / widthPx).roundToInt().coerceAtLeast(1)
            return HeightMap(GRID_WIDTH, h)
        }
    }
}
