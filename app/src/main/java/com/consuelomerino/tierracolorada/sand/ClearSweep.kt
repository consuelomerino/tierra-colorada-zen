package com.consuelomerino.tierracolorada.sand

/** Flattens the whole garden column by column, left to right, over [DURATION_MS]. */
class ClearSweep(private val map: HeightMap) {
    private var clearedColumns = 0

    /** Clears every column reached after [elapsedMs]; returns true once the sweep is finished. */
    fun advance(elapsedMs: Long): Boolean {
        val target = if (elapsedMs >= DURATION_MS) map.width else (map.width * elapsedMs / DURATION_MS).toInt()
        for (x in clearedColumns until target) {
            for (y in 0 until map.height) map[x, y] = 0f
        }
        clearedColumns = maxOf(clearedColumns, target)
        return elapsedMs >= DURATION_MS
    }

    companion object {
        const val DURATION_MS = 600L
    }
}
