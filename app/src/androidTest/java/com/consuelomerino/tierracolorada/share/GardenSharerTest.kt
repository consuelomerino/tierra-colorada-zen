package com.consuelomerino.tierracolorada.share

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.consuelomerino.tierracolorada.life.LifeState
import com.consuelomerino.tierracolorada.sand.SandShader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.consuelomerino.tierracolorada.sand.HeightMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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

    @Test fun imageFileIsMadeOffTheMainThread() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val content = ShareContent(
            1080, 2400, HeightMap(360, 800).apply { this[5, 5] = -0.8f }, emptyList(),
            LifeState(null, emptyList(), 0f, showShadows = false), emptyList(), 0, "Tierra Colorada - Zen",
        )
        var mainThreadTurns = 0
        val uri = runBlocking(Dispatchers.Main) {
            val file = async { GardenSharer.createImageFile(context, content) }
            // If the image were drawn and encoded on the main thread, it would get no turns meanwhile.
            while (!file.isCompleted) {
                yield()
                mainThreadTurns++
            }
            file.await()
        }
        assertTrue("main thread got only $mainThreadTurns turns", mainThreadTurns > 10)
        val bitmap = context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
        assertEquals(1080, bitmap.width)
        assertEquals(2400, bitmap.height)
    }
}
