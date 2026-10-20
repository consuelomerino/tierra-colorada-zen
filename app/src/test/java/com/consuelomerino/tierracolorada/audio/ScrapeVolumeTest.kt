package com.consuelomerino.tierracolorada.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrapeVolumeTest {
    @Test fun silentWhenStill() = assertEquals(0f, scrapeVolume(0f))
    @Test fun cappedAtSixtyPercent() = assertEquals(0.6f, scrapeVolume(500f), 1e-6f)
    @Test fun halfSpeedHalfVolume() = assertEquals(0.3f, scrapeVolume(60f), 1e-6f)
}
