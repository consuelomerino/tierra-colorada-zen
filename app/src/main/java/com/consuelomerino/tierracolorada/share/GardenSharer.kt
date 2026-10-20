package com.consuelomerino.tierracolorada.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.FileProvider
import com.consuelomerino.tierracolorada.R
import com.consuelomerino.tierracolorada.life.LifeState
import com.consuelomerino.tierracolorada.life.Ripple
import com.consuelomerino.tierracolorada.life.drawLife
import com.consuelomerino.tierracolorada.objects.GardenObject
import com.consuelomerino.tierracolorada.objects.ObjectType
import com.consuelomerino.tierracolorada.objects.drawGardenObject
import com.consuelomerino.tierracolorada.sand.CellRect
import com.consuelomerino.tierracolorada.sand.HeightMap
import com.consuelomerino.tierracolorada.sand.SandShader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything a shared picture shows, captured on the main thread; [heights] must be a copy nobody else changes. */
class ShareContent(
    val widthPx: Int,
    val heightPx: Int,
    val heights: HeightMap,
    val objects: List<GardenObject>,
    val life: LifeState,
    val ripples: List<Ripple>,
    val nowMs: Long,
    val watermark: String,
)

/** Turns the garden into a picture (without the toolbar) and hands it to Android's share sheet. */
object GardenSharer {
    private const val WATERMARK_MARGIN_PX = 16f
    private const val WATERMARK_ALPHA = 0.7f

    fun render(
        widthPx: Int,
        heightPx: Int,
        sand: ImageBitmap,
        objects: List<GardenObject>,
        life: LifeState,
        ripples: List<Ripple>,
        nowMs: Long,
        watermark: String,
    ): ImageBitmap {
        val image = ImageBitmap(widthPx, heightPx)
        val cellSize = widthPx.toFloat() / sand.width
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(widthPx.toFloat(), heightPx.toFloat())) {
            drawImage(sand, IntOffset.Zero, IntSize(sand.width, sand.height), IntOffset.Zero, IntSize(widthPx, heightPx), filterQuality = FilterQuality.Low)
            val ordered = objects.sortedBy { it.type != ObjectType.POND }
            for (obj in ordered) drawGardenObject(obj, cellSize)
            drawLife(life, ripples, nowMs, cellSize, objects.firstOrNull { it.type == ObjectType.POND })
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                alpha = (255 * WATERMARK_ALPHA).toInt()
                textSize = heightPx / 60f
                textAlign = Paint.Align.RIGHT
            }
            drawContext.canvas.nativeCanvas.drawText(
                watermark, widthPx - WATERMARK_MARGIN_PX, heightPx - WATERMARK_MARGIN_PX, paint,
            )
        }
        return image
    }

    /** Shades, draws and encodes the picture on a background thread, since that takes a noticeable moment. */
    suspend fun createImageFile(context: Context, content: ShareContent): Uri = withContext(Dispatchers.Default) {
        val image = with(content) { render(widthPx, heightPx, sandImage(heights), objects, life, ripples, nowMs, watermark) }
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "garden.png")
        file.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun openShareSheet(context: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_title)))
    }

    /** Shades the whole sand surface into a fresh image. */
    private fun sandImage(heights: HeightMap): ImageBitmap {
        val pixels = IntArray(heights.width * heights.height)
        SandShader.shade(heights, CellRect(0, 0, heights.width, heights.height), pixels)
        return ImageBitmap(heights.width, heights.height).also {
            it.asAndroidBitmap().setPixels(pixels, 0, heights.width, 0, 0, heights.width, heights.height)
        }
    }
}
