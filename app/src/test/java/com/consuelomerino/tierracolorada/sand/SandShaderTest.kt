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

    @Test fun incrementalShadingMatchesFullShadingAcrossFrameBoundaries() {
        val m = HeightMap(40, 10)
        for (x in 0 until 40) for (y in 0 until 10) m[x, y] = if (y % 3 == 0) -0.8f else 0.3f
        val incremental = IntArray(400)
        SandShader.shade(m, m.takeDirty()!!, incremental)
        // Clear in two "frames", shading only what each frame changed, like the clear sweep does.
        for (x in 0..9) for (y in 0 until 10) m[x, y] = 0f
        SandShader.shade(m, m.takeDirty()!!, incremental)
        for (x in 10..19) for (y in 0 until 10) m[x, y] = 0f
        SandShader.shade(m, m.takeDirty()!!, incremental)
        val full = IntArray(400)
        SandShader.shade(m, CellRect(0, 0, 40, 10), full)
        assertEquals(full.toList(), incremental.toList())
    }
}
