package com.kingzcheung.xime.ui.keyboard

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kingzcheung.xime.glide.GlidePoint
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlideTrailRenderingDeviceTest {
    private fun ink(image: ImageBitmap): Double {
        val pixels = image.toPixelMap()
        var sum = 0.0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val c = pixels[x, y]
            sum += 3.0 - c.red - c.green - c.blue
        }
        return sum
    }

    @Test fun 真机绘制尾迹从渐变变细到完全消失() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val trail = GlideTrailState()
        val density = 2f
        val pts = listOf(
            GlidePoint(20f * density, 80f * density, 1000L, 1),
            GlidePoint(70f * density, 40f * density, 1080L, 1),
            GlidePoint(130f * density, 90f * density, 1160L, 1),
            GlidePoint(190f * density, 50f * density, 1200L, 1),
        )
        trail.update(pts, 5000L)
        fun capture(name: String): ImageBitmap {
            val image = ImageBitmap(440, 240)
            CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(440f, 240f)) {
                drawRect(Color.White)
                drawGlideTrail(trail, Color(0xFF4285F4))
            }
            val dir = File(context.getExternalFilesDir(null), "glide-trail-verification").apply { mkdirs() }
            File(dir, name).outputStream().use {
                image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            return image
        }
        val start = capture("trail-active.png")
        trail.update(emptyList(), 5000L)
        trail.advance(5120L)
        val fading = capture("trail-fading.png")
        assertTrue("残影必须逐渐减淡", ink(start) > ink(fading))
        assertTrue("抬手后仍有短暂尾迹", ink(fading) > 0.0)
        trail.advance(5280L)
        val gone = capture("trail-gone.png")
        assertEquals("到期后不能残留轨迹", 0.0, ink(gone), 0.001)
    }
}
