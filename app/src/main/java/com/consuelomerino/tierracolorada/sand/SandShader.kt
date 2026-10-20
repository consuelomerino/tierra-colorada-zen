package com.consuelomerino.tierracolorada.sand

/** Turns sand heights into colours lit from the top-left, so grooves and ridges read as 3D. */
object SandShader {
    const val BASE_COLOR: Int = 0xFFB5523B.toInt()

    private const val HEIGHT_WEIGHT = 0.15f
    private const val SLOPE_WEIGHT = 0.8f
    private const val MIN_LUMINANCE = 0.55f
    private const val MAX_LUMINANCE = 1.35f

    private val baseRed = (BASE_COLOR shr 16) and 0xFF
    private val baseGreen = (BASE_COLOR shr 8) and 0xFF
    private val baseBlue = BASE_COLOR and 0xFF

    /** Writes opaque ARGB pixels for the cells in [rect] into [out], indexed `y * map.width + x`. */
    fun shade(map: HeightMap, rect: CellRect, out: IntArray) {
        for (y in rect.top until rect.bottom) {
            for (x in rect.left until rect.right) {
                val h = map[x, y]
                val lum = (1 + HEIGHT_WEIGHT * h + SLOPE_WEIGHT * (h - map[x + 1, y + 1]))
                    .coerceIn(MIN_LUMINANCE, MAX_LUMINANCE)
                val r = (baseRed * lum).toInt().coerceIn(0, 255)
                val g = (baseGreen * lum).toInt().coerceIn(0, 255)
                val b = (baseBlue * lum).toInt().coerceIn(0, 255)
                out[y * map.width + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }
}
