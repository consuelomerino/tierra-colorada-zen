package com.consuelomerino.tierracolorada.objects

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Seeded geometry for code-drawn objects, so each object looks the same every time it is drawn. */
object ObjectShapes {
    private const val STONE_VARIATION = 0.15f
    private const val FROND_COUNT = 7
    private const val FROND_JITTER = 0.15f

    /** Irregular rounded outline around (0, 0); each point's distance varies ±15 % from [radius]. */
    fun stoneOutline(seed: Int, radius: Float, points: Int = 24): List<Pair<Float, Float>> {
        val random = Random(seed)
        return List(points) { i ->
            val angle = 2 * PI * i / points
            val r = radius * (1 + (random.nextFloat() * 2 - 1) * STONE_VARIATION)
            (r * cos(angle)).toFloat() to (r * sin(angle)).toFloat()
        }
    }

    /** Angles (radians) of a fern's fronds, evenly spread with a little seeded jitter. */
    fun fernFronds(seed: Int): List<Float> {
        val random = Random(seed)
        return List(FROND_COUNT) { i ->
            (2 * PI * i / FROND_COUNT).toFloat() + (random.nextFloat() * 2 - 1) * FROND_JITTER
        }
    }
}
