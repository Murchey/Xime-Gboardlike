package com.kingzcheung.xime.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kingzcheung.xime.settings.BackgroundConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** 角度制 → 渐变起始终止点（归一化坐标 0..1）。 */
private fun angleToFractionalPoints(angleDeg: Int): Pair<Offset, Offset> {
    val rad = Math.toRadians(angleDeg.toDouble())
    val cosA = cos(rad)
    val sinA = sin(rad)
    val divisor = (abs(cosA) + abs(sinA)).coerceAtLeast(1e-10)
    return Offset(
        (0.5f - (cosA / divisor * 0.5f)).toFloat().coerceIn(0f, 1f),
        (0.5f - (sinA / divisor * 0.5f)).toFloat().coerceIn(0f, 1f),
    ) to Offset(
        (0.5f + (cosA / divisor * 0.5f)).toFloat().coerceIn(0f, 1f),
        (0.5f + (sinA / divisor * 0.5f)).toFloat().coerceIn(0f, 1f),
    )
}

/** 从 BackgroundConfig 解析出像素色值，供不支持渐变/图片的场景使用。 */
fun resolveSolidColorHex(config: BackgroundConfig, isDark: Boolean): Long? {
    return when (config.type) {
        "solid" -> if (isDark) config.colorDark ?: config.color else config.color
        "gradient" -> {
            val hexes = if (isDark) config.colorsDark ?: config.colors else config.colors
            hexes?.firstOrNull()
        }
        else -> null
    }
}

/** 从 BackgroundConfig 解析出 Compose Color。 */
fun resolveSolidColor(config: BackgroundConfig, isDark: Boolean): Color? {
    return resolveSolidColorHex(config, isDark)?.let { Color(0xFF000000 or it) }
}

/** 解码后图片的绘制区域（容器坐标，可越界，绘制侧用 clipRect 裁剪）。 */
internal data class ImagePlacement(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

/**
 * 图片填充 + 平移：
 * - cover/fill_width/fit_height 等"超出容器"的模式，[offsetX]/[offsetY] 在溢出余量内移动；
 * - contain/cover 之类留有边距时，0 = 靠左/上，0.5 = 居中（默认），1 = 靠右/下；
 * - fill 与 none 不接受平移（没有余量或按原尺寸绘制）。
 */
internal fun imagePlacement(
    srcW: Float,
    srcH: Float,
    dstW: Float,
    dstH: Float,
    fit: String,
    offsetX: Float?,
    offsetY: Float?,
): ImagePlacement {
    if (srcW <= 0f || srcH <= 0f || dstW <= 0f || dstH <= 0f) {
        return ImagePlacement(0f, 0f, 0f, 0f)
    }
    val px = (offsetX ?: 0.5f).coerceIn(0f, 1f)
    val py = (offsetY ?: 0.5f).coerceIn(0f, 1f)

    fun covered(scale: Float): ImagePlacement {
        val w = srcW * scale
        val h = srcH * scale
        // 有余量时按比例平移（0 = 左/上边缘对齐，1 = 右/下边缘对齐）；
        // 比容器小时在留下的边距里平移（默认居中）。
        val left = if (w > dstW) -(w - dstW) * px else (dstW - w) * px
        val top = if (h > dstH) -(h - dstH) * py else (dstH - h) * py
        return ImagePlacement(left, top, w, h)
    }

    return when (fit) {
        "contain" -> {
            val scale = min(dstW / srcW, dstH / srcH)
            val w = srcW * scale
            val h = srcH * scale
            ImagePlacement((dstW - w) * px, (dstH - h) * py, w, h)
        }
        "fill" -> ImagePlacement(0f, 0f, dstW, dstH)
        "fit_width" -> {
            val scale = dstW / srcW
            val w = srcW * scale
            val h = srcH * scale
            ImagePlacement(0f, if (h > dstH) -(h - dstH) * py else (dstH - h) * py, w, h)
        }
        "fit_height" -> {
            val scale = dstH / srcH
            val w = srcW * scale
            val h = srcH * scale
            ImagePlacement(if (w > dstW) -(w - dstW) * px else (dstW - w) * px, 0f, w, h)
        }
        "none" -> ImagePlacement(0f, 0f, srcW, srcH)
        else -> covered(max(dstW / srcW, dstH / srcH)) // cover（默认）
    }.zeroSafe()
}

/** 去掉 -0.0f：它和 0.0f 在数据类里不相等，会给测试与缓存比较添麻烦。 */
private fun ImagePlacement.zeroSafe(): ImagePlacement =
    ImagePlacement(left + 0f, top + 0f, width + 0f, height + 0f)

/**
 * 应用 BackgroundConfig 背景到 Composable。
 *
 * - solid / gradient：直接用 [Modifier.background]
 * - image：用 [Modifier.drawWithContent] 在内容下方绘制图片（支持拖动平移与蒙版模糊）
 */
@Composable
fun Modifier.keyboardBackground(
    background: BackgroundConfig?,
    isDark: Boolean,
    fallbackColor: Color = Color(0xFFE3E4E8),
): Modifier {
    if (background == null) return this.then(Modifier.background(fallbackColor))

    when (background.type) {
        "solid" -> {
            val hex = if (isDark) background.colorDark ?: background.color else background.color
            val color = hex?.let { Color(0xFF000000 or it) }
            return if (color != null) this.then(Modifier.background(color))
            else this.then(Modifier.background(fallbackColor))
        }
        "gradient" -> {
            val hexes = if (isDark) background.colorsDark ?: background.colors else background.colors
            if (hexes != null && hexes.size >= 2) {
                val colors = hexes.map { Color(0xFF000000 or it) }
                val (startRatio, endRatio) = angleToFractionalPoints(background.angle ?: 0)
                return this.then(
                    Modifier.drawWithContent {
                        val start = Offset(size.width * startRatio.x, size.height * startRatio.y)
                        val end = Offset(size.width * endRatio.x, size.height * endRatio.y)
                        drawRect(
                            brush = Brush.linearGradient(colors, start, end),
                            size = size,
                        )
                        drawContent()
                    }
                )
            }
            return this.then(Modifier.background(fallbackColor))
        }
        "image" -> {
            val context = LocalContext.current
            val src = if (isDark) background.srcDark ?: background.src else background.src
            if (src.isNullOrBlank()) return this.then(Modifier.background(fallbackColor))
            val maskBlurRadius = background.maskBlurRadius ?: 0

            var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(src, maskBlurRadius) {
                imageBitmap = try {
                    withContext(Dispatchers.Default) {
                        decodeBackgroundBitmap(context, src)
                            ?.let { raw ->
                                val blurred = if (maskBlurRadius > 0) blurBitmap(raw, maskBlurRadius.toFloat()) else raw
                                if (blurred !== raw) raw.recycle()
                                blurred.asImageBitmap()
                            }
                    }
                } catch (e: Exception) { null }
            }

            val bitmap = imageBitmap
            if (bitmap != null) {
                val fit = background.fit ?: "cover"
                val overlayAlpha = if (isDark) {
                    background.overlayAlphaDark ?: background.overlayAlpha
                } else {
                    background.overlayAlpha
                }
                return this.then(
                    Modifier.drawWithContent {
                        drawImageBackground(bitmap, fit, background.offsetX, background.offsetY)
                        // 半透明黑色遮罩压暗背景，提升按键对比度
                        if (overlayAlpha != null && overlayAlpha > 0f) {
                            drawRect(
                                color = Color.Black.copy(alpha = overlayAlpha.coerceIn(0f, 1f)),
                            )
                        }
                        drawContent()
                    }
                )
            }
            return this.then(Modifier.background(fallbackColor))
        }
        else -> return this.then(Modifier.background(fallbackColor))
    }
}

/** 在 DrawScope 内绘制图片背景（含平移）。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawImageBackground(
    bitmap: ImageBitmap,
    fit: String,
    offsetX: Float? = null,
    offsetY: Float? = null,
) {
    val placement = imagePlacement(
        srcW = bitmap.width.toFloat(),
        srcH = bitmap.height.toFloat(),
        dstW = size.width,
        dstH = size.height,
        fit = fit,
        offsetX = offsetX,
        offsetY = offsetY,
    )
    if (placement.width <= 0f || placement.height <= 0f) return
    clipRect {
        drawImage(
            bitmap,
            dstOffset = IntOffset(placement.left.roundToInt(), placement.top.roundToInt()),
            dstSize = IntSize(placement.width.roundToInt(), placement.height.roundToInt()),
        )
    }
}

// ── 底图解码 / 模糊（预览、键盘、毛玻璃键帽共用） ──

/** 底图解码最长边上限：键盘只占屏幕一条，没必要按原图分辨率吃内存。 */
private const val MAX_BACKGROUND_EDGE = 2048

/** 读取主题图片并解码（按 [MAX_BACKGROUND_EDGE] 下采样）；失败返回 null。 */
internal fun decodeBackgroundBitmap(context: android.content.Context, src: String): Bitmap? {
    val bytes = openThemeImageStream(context, src)?.use { it.readBytes() } ?: return null
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth > 0 && bounds.outHeight > 0 &&
        max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_BACKGROUND_EDGE
    ) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

/**
 * 高斯模糊（3 次盒式模糊近似），纯 CPU、全版本可用。
 *
 * 不用 [android.graphics.RenderEffect]：它只在硬件画布上生效，而这里是离屏位图；
 * 模糊是一次性的（换主题/调半径时才跑），放在 IO/Default 线程上，代价可接受。
 */
internal fun blurBitmap(src: Bitmap, radiusPx: Float): Bitmap {
    if (radiusPx < 1f || src.width < 2 || src.height < 2) return src
    val radius = (radiusPx / 2f).roundToInt().coerceIn(1, maxOf(1, minOf(src.width, src.height) / 2))
    return try {
        val w = src.width
        val h = src.height
        val buffer = IntArray(w * h)
        src.getPixels(buffer, 0, w, 0, 0, w, h)
        val scratch = IntArray(w * h)
        var current = buffer
        var spare = scratch
        repeat(3) {
            boxBlurHorizontal(current, spare, w, h, radius)
            boxBlurVertical(spare, current, w, h, radius)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(current, 0, w, 0, 0, w, h)
        out
    } catch (e: Exception) {
        src
    }
}

private fun boxBlurHorizontal(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val div = radius * 2 + 1
    for (y in 0 until h) {
        val row = y * w
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val px = src[row + i.coerceIn(0, w - 1)]
            a += px ushr 24 and 0xFF
            r += px ushr 16 and 0xFF
            g += px ushr 8 and 0xFF
            b += px and 0xFF
        }
        for (x in 0 until w) {
            dst[row + x] = ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
            val out = src[row + (x - radius).coerceIn(0, w - 1)]
            val into = src[row + (x + radius + 1).coerceIn(0, w - 1)]
            a += (into ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
            r += (into ushr 16 and 0xFF) - (out ushr 16 and 0xFF)
            g += (into ushr 8 and 0xFF) - (out ushr 8 and 0xFF)
            b += (into and 0xFF) - (out and 0xFF)
        }
    }
}

private fun boxBlurVertical(src: IntArray, dst: IntArray, w: Int, h: Int, radius: Int) {
    val div = radius * 2 + 1
    for (x in 0 until w) {
        var a = 0
        var r = 0
        var g = 0
        var b = 0
        for (i in -radius..radius) {
            val px = src[i.coerceIn(0, h - 1) * w + x]
            a += px ushr 24 and 0xFF
            r += px ushr 16 and 0xFF
            g += px ushr 8 and 0xFF
            b += px and 0xFF
        }
        for (y in 0 until h) {
            dst[y * w + x] = ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
            val out = src[(y - radius).coerceIn(0, h - 1) * w + x]
            val into = src[(y + radius + 1).coerceIn(0, h - 1) * w + x]
            a += (into ushr 24 and 0xFF) - (out ushr 24 and 0xFF)
            r += (into ushr 16 and 0xFF) - (out ushr 16 and 0xFF)
            g += (into ushr 8 and 0xFF) - (out ushr 8 and 0xFF)
            b += (into and 0xFF) - (out and 0xFF)
        }
    }
}

/**
 * 把底图按 [config] 的填充/平移规则绘制成 [widthPx] × [heightPx] 的位图，再模糊 [blurRadiusPx]。
 * 供「按键毛玻璃」在键帽内采样（键帽只画这块位图中自己那一小块）。
 */
internal suspend fun renderFrostedBackdrop(
    context: android.content.Context,
    config: BackgroundConfig,
    isDark: Boolean,
    widthPx: Int,
    heightPx: Int,
    blurRadiusPx: Float,
): ImageBitmap? = withContext(Dispatchers.Default) {
    if (widthPx <= 0 || heightPx <= 0) return@withContext null
    val src = if (isDark) config.srcDark ?: config.src else config.src
    if (src.isNullOrBlank()) return@withContext null
    val raw = decodeBackgroundBitmap(context, src) ?: return@withContext null
    try {
        val placement = imagePlacement(
            srcW = raw.width.toFloat(),
            srcH = raw.height.toFloat(),
            dstW = widthPx.toFloat(),
            dstH = heightPx.toFloat(),
            fit = config.fit ?: "cover",
            offsetX = config.offsetX,
            offsetY = config.offsetY,
        )
        val canvasBitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        Canvas(canvasBitmap).drawBitmap(
            raw,
            null,
            RectF(
                placement.left,
                placement.top,
                placement.left + placement.width,
                placement.top + placement.height,
            ),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        val blurred = blurBitmap(canvasBitmap, blurRadiusPx)
        if (blurred !== canvasBitmap) canvasBitmap.recycle()
        blurred.asImageBitmap()
    } catch (e: Exception) {
        null
    } finally {
        raw.recycle()
    }
}
