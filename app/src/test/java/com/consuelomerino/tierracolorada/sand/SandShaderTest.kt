package com.consuelomerino.tierracolorada.sand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SandShaderTest {
    private fun red(c: Int) = (c shr 16) and 0xFF

    @Test fun flatSandIsBaseColor() {
        val m = HeightMap(3, 3)
        val out = IntArray(9)
        SandShader.shade(m, CellRect(0, 0, 3, 3), out)
        assertEquals(SandShader.BASE_COLOR, out[4])
    }

    @Test fun ridgeFacingLightIsBrighter() {
        val m = HeightMap(3, 3)
        m[1, 1] = 0.5f
        val out = IntArray(9)
        SandShader.shade(m, CellRect(0, 0, 3, 3), out)
        assertTrue(red(out[4]) > red(SandShader.BASE_COLOR))
    }

    @Test fun grooveIsDarker() {
        val m = HeightMap(3, 3)
        m[1, 1] = -0.5f
        val out = IntArray(9)
        SandShader.shade(m, CellRect(0, 0, 3, 3), out)
        assertTrue(red(out[4]) < red(SandShader.BASE_COLOR))
    }

    @Test fun onlyRectIsWritten() {
        val m = HeightMap(3, 3)
        val out = IntArray(9)
        SandShader.shade(m, CellRect(0, 0, 1, 1), out)
        assertEquals(0, out[8])
    }
}
