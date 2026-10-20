package com.consuelomerino.tierracolorada.objects

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectShapesTest {
    @Test fun stoneOutlineIsDeterministic() =
        assertEquals(ObjectShapes.stoneOutline(7, 10f), ObjectShapes.stoneOutline(7, 10f))

    @Test fun stoneOutlineVariesBySeed() =
        assertNotEquals(ObjectShapes.stoneOutline(7, 10f), ObjectShapes.stoneOutline(8, 10f))

    @Test fun stoneRadiusWithinFifteenPercent() {
        ObjectShapes.stoneOutline(3, 10f).forEach { (x, y) -> assertTrue(hypot(x, y) in 8.5f..11.5f) }
    }
}
