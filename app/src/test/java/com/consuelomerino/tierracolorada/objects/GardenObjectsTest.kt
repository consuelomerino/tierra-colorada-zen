package com.consuelomerino.tierracolorada.objects

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GardenObjectsTest {
    private fun garden() = GardenObjects(360, 800)

    private fun assertOutsidePond(o: GardenObject, p: GardenObject) =
        assertTrue("overlaps pond", hypot(o.x - p.x, o.y - p.y) >= p.radius + o.radius)

    @Test fun limitIsThirty() {
        val g = garden()
        repeat(30) { assertNotNull(g.add(ObjectType.STONE, 10f + it * 10, 10f, it)) }
        assertNull(g.add(ObjectType.FERN, 100f, 400f, 0))
        assertFalse(g.canAdd(ObjectType.STONE))
    }

    @Test fun onlyOnePond() {
        val g = garden()
        g.add(ObjectType.POND, 180f, 400f, 1)
        assertFalse(g.canAdd(ObjectType.POND))
        assertTrue(g.canAdd(ObjectType.STONE))
    }

    @Test fun stoneDroppedOnPondMovesOutside() {
        val g = garden()
        val p = g.add(ObjectType.POND, 180f, 400f, 1)!!
        val s = g.add(ObjectType.STONE, 185f, 400f, 2)!!
        assertOutsidePond(s, p)
    }

    @Test fun pondMovedOntoStonePushesStone() {
        val g = garden()
        val s = g.add(ObjectType.STONE, 100f, 100f, 2)!!
        g.add(ObjectType.POND, 300f, 600f, 1)!!.let { g.move(it.id, 100f, 100f) }
        assertOutsidePond(g.items.first { it.id == s.id }, g.pond()!!)
    }

    @Test fun objectsMayOverlapEachOther() {
        val g = garden()
        g.add(ObjectType.STONE, 50f, 50f, 1)
        val f = g.add(ObjectType.FERN, 52f, 50f, 2)!!
        assertEquals(52f, f.x)
    }

    @Test fun dropOutsideGardenIsClamped() {
        val g = garden()
        val s = g.add(ObjectType.STONE, -20f, 900f, 1)!!
        assertEquals(0f, s.x)
        assertEquals(800f, s.y)
    }

    @Test fun pondPushNearEdgeStaysInBoundsAndOutsidePond() {
        val g = garden()
        val p = g.add(ObjectType.POND, 20f, 400f, 1)!!
        val s = g.add(ObjectType.STONE, 5f, 400f, 2)!!
        assertTrue(s.x in 0f..360f)
        assertOutsidePond(s, p)
    }

    @Test fun scaleIsClamped() {
        val g = garden()
        val s = g.add(ObjectType.STONE, 50f, 50f, 1)!!
        assertEquals(2f, g.transform(s.id, 9f, 0f)!!.scale)
    }

    @Test fun isBlockedUnderFootprint() {
        val g = garden()
        g.add(ObjectType.STONE, 50f, 50f, 1)
        assertTrue(g.isBlocked(50, 50))
        assertFalse(g.isBlocked(80, 50))
    }
}
