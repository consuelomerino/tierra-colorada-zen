package com.consuelomerino.tierracolorada.life

import org.junit.Assert.assertEquals
import org.junit.Test

class PondRipplesTest {
    @Test fun rippleExpiresAfterTwoSeconds() {
        val p = PondRipples()
        p.tap(1f, 1f, 0)
        assertEquals(1, p.active(1999).size)
        assertEquals(0, p.active(2000).size)
    }
}
