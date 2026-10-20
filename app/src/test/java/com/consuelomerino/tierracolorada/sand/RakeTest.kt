package com.consuelomerino.tierracolorada.sand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RakeTest {
    private fun map() = HeightMap(100, 100)

    @Test fun singleStampCarvesFourTroughsAcrossVerticalLine() {
        val m = map()
        Rake(m).apply { beginStroke(50f, 50f); endStroke() }
        // Initial direction (1,0) → tines spread along y at 50 ± 4 and 50 ± 12.
        listOf(38, 46, 54, 62).forEach { y -> assertEquals(Rake.GROOVE_DEPTH, m[50, y], 1e-3f) }
    }

    @Test fun ridgeSitsBesideTrough() {
        val m = map()
        Rake(m).apply { beginStroke(50f, 50f); endStroke() }
        assertTrue(m[50, 65] > 0.2f) // d = 3 from the tine at y = 62
    }

    @Test fun fastStrokeLeavesNoGaps() {
        val m = map()
        Rake(m).apply { beginStroke(10f, 50f); moveTo(90f, 50f); moveTo(90f, 50f); moveTo(90f, 50f); endStroke() }
        for (x in 12..88) assertTrue("gap at $x", m[x, 46] < -0.5f)
    }

    @Test fun blockedCellsAreUntouched() {
        val m = map()
        Rake(m) { x, _ -> x in 40..60 }.apply { beginStroke(10f, 50f); repeat(3) { moveTo(90f, 50f) }; endStroke() }
        for (x in 40..60) assertEquals(0f, m[x, 46])
    }

    @Test fun newStrokeDoesNotConnectToPreviousStroke() {
        val m = map()
        val r = Rake(m)
        r.beginStroke(10f, 20f); r.endStroke()
        r.beginStroke(90f, 80f); r.endStroke()
        assertEquals(0f, m[50, 50])
    }

    @Test fun strokeOffGridDoesNotCrash() {
        val m = map()
        Rake(m).apply { beginStroke(-30f, -30f); moveTo(130f, 130f); endStroke() }
    }

    @Test fun stationaryFingerProducesNoNaN() {
        val m = map()
        Rake(m).apply { beginStroke(50f, 50f); repeat(5) { moveTo(50f, 50f) }; endStroke() }
        assertFalse(m.values.any { it.isNaN() })
    }

    @Test fun moveToReturnsDistanceTravelled() {
        val r = Rake(map())
        r.beginStroke(10f, 10f)
        val d = (1..3).sumOf { r.moveTo(40f, 10f).toDouble() }
        assertTrue("d = $d", d > 15 && d <= 30.01)
    }
}
