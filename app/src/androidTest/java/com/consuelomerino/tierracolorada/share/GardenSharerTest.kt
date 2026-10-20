package com.consuelomerino.tierracolorada.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.consuelomerino.tierracolorada.life.LifeState
import com.consuelomerino.tierracolorada.sand.SandShader
import org.junit.Assert.assertEquals
import org.junit.Test

class GardenSharerTest {
    @Test fun renderHasRequestedSizeAndSandColor() {
        val sand = ImageBitmap(36, 80).also { b -> b.asAndroidBitmap().eraseColor(SandShader.BASE_COLOR) }
        val img = GardenSharer.render(
            360, 800, sand, emptyList(), LifeState(null, emptyList(), 0f, showShadows = false),
            emptyList(), 0, "Tierra Colorada - Zen",
        )
        assertEquals(360, img.width)
        assertEquals(SandShader.BASE_COLOR, img.asAndroidBitmap().getPixel(10, 10))
    }
}
