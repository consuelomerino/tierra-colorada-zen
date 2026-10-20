package com.consuelomerino.tierracolorada.objects

import kotlin.math.hypot

/** The objects in the garden and the rules for where they may go. */
class GardenObjects(
    val gridWidth: Int,
    val gridHeight: Int,
    initial: List<GardenObject> = emptyList(),
) {
    private val objects = initial.toMutableList()
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0L) + 1

    val items: List<GardenObject> get() = objects.toList()

    fun pond(): GardenObject? = objects.firstOrNull { it.type == ObjectType.POND }

    fun canAdd(type: ObjectType): Boolean =
        objects.size < MAX_OBJECTS && !(type == ObjectType.POND && pond() != null)

    fun add(type: ObjectType, x: Float, y: Float, seed: Int): GardenObject? {
        if (!canAdd(type)) return null
        return place(GardenObject(nextId++, type, x, y, seed = seed), isNew = true)
    }

    fun move(id: Long, x: Float, y: Float): GardenObject? =
        find(id)?.let { place(it.copy(x = x, y = y), isNew = false) }

    fun transform(id: Long, scale: Float, rotation: Float): GardenObject? =
        find(id)?.let { place(it.copy(scale = scale.coerceIn(MIN_SCALE, MAX_SCALE), rotation = rotation), isNew = false) }

    fun remove(id: Long) {
        objects.removeAll { it.id == id }
    }

    fun isBlocked(cellX: Int, cellY: Int): Boolean =
        objects.any { it.covers(cellX + 0.5f, cellY + 0.5f) }

    private fun find(id: Long) = objects.firstOrNull { it.id == id }

    private fun place(candidate: GardenObject, isNew: Boolean): GardenObject {
        var placed = clamp(candidate)
        if (placed.type == ObjectType.POND) {
            store(placed, isNew)
            for (i in objects.indices) {
                if (objects[i].type != ObjectType.POND) objects[i] = keepOutOfPond(objects[i], placed)
            }
        } else {
            pond()?.let { placed = keepOutOfPond(placed, it) }
            store(placed, isNew)
        }
        return placed
    }

    private fun store(obj: GardenObject, isNew: Boolean) {
        if (isNew) objects += obj else objects[objects.indexOfFirst { it.id == obj.id }] = obj
    }

    private fun clamp(o: GardenObject) =
        o.copy(x = o.x.coerceIn(0f, gridWidth.toFloat()), y = o.y.coerceIn(0f, gridHeight.toFloat()))

    private fun overlaps(o: GardenObject, pond: GardenObject) =
        hypot(o.x - pond.x, o.y - pond.y) < pond.radius + o.radius

    /** Pushes [o] straight away from the pond's centre until it no longer overlaps; tries the other side if an edge is in the way. */
    private fun keepOutOfPond(o: GardenObject, pond: GardenObject): GardenObject {
        if (!overlaps(o, pond)) return o
        var dx = o.x - pond.x
        var dy = o.y - pond.y
        val length = hypot(dx, dy)
        if (length == 0f) { dx = 1f; dy = 0f } else { dx /= length; dy /= length }
        val distance = pond.radius + o.radius + 1
        val pushed = clamp(o.copy(x = pond.x + dx * distance, y = pond.y + dy * distance))
        if (!overlaps(pushed, pond)) return pushed
        return clamp(o.copy(x = pond.x - dx * distance, y = pond.y - dy * distance))
    }

    companion object {
        const val MAX_OBJECTS = 30
        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 2.0f
    }
}
