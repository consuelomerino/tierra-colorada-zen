package com.consuelomerino.tierracolorada.sand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearSweepTest {
    @Test fun halfwayClearsLeftHalf() {
        val m = HeightMap(10, 4).apply { values.fill(-0.5f) }
        assertFalse(ClearSweep(m).advance(300))
        assertEquals(0f, m[4, 2])
        assertEquals(-0.5f, m[5, 2])
    }

    @Test fun finishedClearsEverything() {
        val m = HeightMap(10, 4).apply { values.fill(-0.5f) }
        assertTrue(ClearSweep(m).advance(600))
        assertTrue(m.values.all { it == 0f })
    }
}
