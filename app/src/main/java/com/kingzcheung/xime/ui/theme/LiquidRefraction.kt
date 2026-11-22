package com.kingzcheung.xime.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

/**
 * 液态玻璃的图片折射（安卓 13+）：只在键帽外圈约 bevel 宽度内采样并弯折键盘底图，
 * 保留轻微色散（红/绿/蓝取样偏移不同），键中央仍然直接透出已经画好的底图。
 *
 * 底图与键位都来自 [FrostedBackdropHolder]（自定义图片背景时才有），
 * 非图片背景、系统不支持 AGSL 或液态玻璃没开时，调用方直接跳过。
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object LiquidRefraction {

    private const val SOURCE = """
        uniform shader image;
        uniform float2 iSize;
        uniform float radius;
        uniform float bevel;
        uniform float strength;
        uniform float spread;
        uniform float2 imageOrigin;
        uniform float2 pixelsPerUnit;
        half4 main(float2 p) {
            float2 c = iSize * 0.5;
            float r = min(radius, min(c.x, c.y));
            float2 rel = p - c;
            float2 q = abs(rel) - c + r;
            float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            if (d > 0.5 || d <= -bevel) return half4(0.0);
            float2 g = (q.x > 0.0 && q.y > 0.0) ? normalize(q)
                : ((q.x > q.y) ? float2(1.0, 0.0) : float2(0.0, 1.0));
            float2 n = g * sign(rel);
            float e = 1.0 - clamp(-d / bevel, 0.0, 1.0);
            float2 uv = (p - imageOrigin) * pixelsPerUnit;
            float2 off = -n * (strength * e * e * e) * pixelsPerUnit;
            half4 red = image.eval(uv + off);
            half4 green = image.eval(uv + off * (1.0 + spread));
            half4 blue = image.eval(uv + off * (1.0 + 2.0 * spread));
            half coverage = min(red.a, min(green.a, blue.a));
            return half4(min(half3(red.r, green.g, blue.b), half3(coverage)), coverage)
                * half(e * clamp(0.5 - d, 0.0, 1.0));
        }
    """

    private val shader: RuntimeShader? by lazy { runCatching { RuntimeShader(SOURCE) }.getOrNull() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var boundBitmap: Bitmap? = null
    private var bitmapShader: BitmapShader? = null

    fun available(): Boolean = shader != null

    private fun imageShaderFor(bitmap: Bitmap): BitmapShader? {
        if (boundBitmap === bitmap) return bitmapShader
        val created = BitmapShader(
            bitmap,
            android.graphics.Shader.TileMode.DECAL,
            android.graphics.Shader.TileMode.DECAL,
        ).apply { setFilterMode(BitmapShader.FILTER_MODE_LINEAR) }
        boundBitmap = bitmap
        bitmapShader = created
        return created
    }

    fun DrawScope.drawRefraction(
        image: ImageBitmap,
        keyLeft: Float,
        keyTop: Float,
        radiusPx: Float,
        strength: LiquidGlassStrength,
        imageLeft: Float,
        imageTop: Float,
        pixelsPerX: Float,
        pixelsPerY: Float,
    ) {
        val shader = shader ?: return
        val bitmap = image.asAndroidBitmap()
        val imageShader = imageShaderFor(bitmap) ?: return
        val density = this.density
        shader.setInputShader("image", imageShader)
        shader.setFloatUniform("iSize", size.width, size.height)
        shader.setFloatUniform("radius", radiusPx)
        shader.setFloatUniform("bevel", strength.bevelDp * density)
        shader.setFloatUniform("strength", strength.bendDp * density)
        shader.setFloatUniform("spread", strength.spread)
        shader.setFloatUniform("imageOrigin", imageLeft, imageTop)
        shader.setFloatUniform("pixelsPerUnit", pixelsPerX, pixelsPerY)
        paint.shader = shader
        drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
        paint.shader = null
    }
}
