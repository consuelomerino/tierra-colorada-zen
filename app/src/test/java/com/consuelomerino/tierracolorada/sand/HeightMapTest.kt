package com.consuelomerino.tierracolorada.sand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeightMapTest {
    @Test fun forScreenUsesGridWidthAndAspect() {
        val m = HeightMap.forScreen(1080, 2400)
        assertEquals(360, m.width)
        assertEquals(800, m.height)
    }

    @Test fun setClampsToUnitRange() {
        val m = HeightMap(10, 10)
        m[1, 1] = 5f
        m[2, 2] = -5f
        assertEquals(1f, m[1, 1])
        assertEquals(-1f, m[2, 2])
    }

    @Test fun setOutOfBoundsIsIgnored() {
        val m = HeightMap(10, 10)
        m[-1, 0] = 1f
        m[10, 5] = 1f
        m[3, 99] = 1f
        assertNull(m.takeDirty())
        assertEquals(0f, m[-1, 0])
    }

    @Test fun dirtyRectCoversWritesAndResets() {
        val m = HeightMap(10, 10)
        m[2, 3] = .5f
        m[6, 1] = .5f
        assertEquals(CellRect(2, 1, 7, 4), m.takeDirty())
        assertNull(m.takeDirty())
    }

    @Test fun markAllDirtyCoversGrid() {
        val m = HeightMap(10, 8)
        m.markAllDirty()
        assertEquals(CellRect(0, 0, 10, 8), m.takeDirty())
    }

    @Test fun resamplePreservesValuesAtCorners() {
        val m = HeightMap(4, 4)
        m[0, 0] = 1f
        m[3, 3] = -1f
        val r = m.resampledTo(8, 8)
        assertEquals(1f, r[0, 0], 1e-4f)
        assertEquals(-1f, r[7, 7], 1e-4f)
        assertEquals(8, r.width)
    }
}
