package com.consuelomerino.tierracolorada.sand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun writesDoNotAllocate() {
        // Raking writes thousands of cells per frame; allocating per write makes the garbage collector stutter.
        val m = HeightMap(360, 800)
        val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val id = Thread.currentThread().id
        repeat(1_000) { m[it % 360, it % 800] = -0.5f } // warm up
        m.takeDirty()
        val before = threads.getThreadAllocatedBytes(id)
        for (i in 0 until 100_000) m[i % 360, (i / 360) % 800] = -0.5f
        val allocated = threads.getThreadAllocatedBytes(id) - before
        assertTrue("100 000 writes allocated $allocated bytes", allocated < 50_000)
        assertEquals(CellRect(0, 0, 360, 278), m.takeDirty())
    }

    @Test fun dirtyAreaCoversEveryWrite() {
        val m = HeightMap(20, 20)
        m[5, 7] = 0.1f
        m[2, 9] = 0.1f
        m[11, 3] = 0.1f
        assertEquals(CellRect(2, 3, 12, 10), m.takeDirty())
        assertNull(m.takeDirty())
        m.markAllDirty()
        m[5, 5] = 0.2f
        assertEquals(CellRect(0, 0, 20, 20), m.takeDirty())
    }
}
