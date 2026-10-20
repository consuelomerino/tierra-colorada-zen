package com.consuelomerino.tierracolorada.sand

import org.junit.Assert.assertEquals
import org.junit.Test

class SandToolsTest {
    @Test fun smoothMovesTowardZero() {
        val m = HeightMap(50, 50)
        m[25, 25] = -1f
        SandTools.smooth(m, 25f, 25f)
        assertEquals(-0.8f, m[25, 25], 1e-4f)
    }

    @Test fun smoothLeavesFarCellsAlone() {
        val m = HeightMap(50, 50)
        m[0, 0] = -1f
        SandTools.smooth(m, 40f, 40f)
        assertEquals(-1f, m[0, 0])
    }

    @Test fun ripplesFormThreeRings() {
        val m = HeightMap(100, 100)
        SandTools.stampRipples(m, 50f, 50f, 10f)
        listOf(63, 66, 69).forEach { assertEquals(SandTools.RIPPLE_DEPTH, m[it, 50], 1e-3f) }
        assertEquals(0f, m[50, 50])
    }
}
