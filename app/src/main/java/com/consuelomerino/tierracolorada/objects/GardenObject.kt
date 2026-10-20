package com.consuelomerino.tierracolorada.objects

import kotlin.math.abs

/** Kinds of objects the player can place; [baseRadius] is the footprint at scale 1, in grid cells. */
enum class ObjectType(val baseRadius: Float) {
    STONE(14f),
    FERN(22f),
    HELICONIA(16f),
    POND(40f),
}

/**
 * One placed object. Position is its centre in grid cells, [rotation] is in radians,
 * and [seed] keeps its generated shape the same across saves.
 */
data class GardenObject(
    val id: Long,
    val type: ObjectType,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val seed: Int,
) {
    val radius: Float get() = type.baseRadius * scale

    /** Checked for every cell the rake touches, so the cheap box test comes first and there is no square root. */
    fun covers(cx: Float, cy: Float): Boolean {
        val r = radius
        val dx = cx - x
        val dy = cy - y
        return abs(dx) <= r && abs(dy) <= r && dx * dx + dy * dy <= r * r
    }
}
