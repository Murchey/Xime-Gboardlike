package com.kingzcheung.xime.glide.neural

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.kingzcheung.xime.BuildConfig
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.glide.GlideGeometryDecoder
import com.kingzcheung.xime.glide.GlideGeometrySnapshot
import com.kingzcheung.xime.glide.GlideHypothesisProvider
import com.kingzcheung.xime.glide.GlidePoint
import com.kingzcheung.xime.glide.GlideSpellingTrie
import com.kingzcheung.xime.glide.OpenGlideSpellingConstraint

/**
 * 把「轨迹 → FUTO emissions → 受方案约束的 CTC beam」接成假设来源。
 *
 * 模型未就绪、ABI 不支持、推理/解码失败时返回 null，调用方回退几何解码。
 */
class FutoHypothesisProvider(private val context: Context) : GlideHypothesisProvider {

    override fun hypotheses(
        points: List<GlidePoint>,
        geometry: GlideGeometrySnapshot,
        limit: Int,
        spellings: Collection<String>?,
        densityDpi: Float,
    ): List<GlideGeometryDecoder.Hypothesis>? {
        if (!SettingsPreferences.isGlideNeuralEnabled(context)) return null
        if (!FutoSwipeEncoder.ensureInitialized(context)) return null
        val prepared = FutoFeaturePreparer.prepare(points, geometry) ?: return null
        val start = SystemClock.elapsedRealtime()
        val emissions = FutoSwipeEncoder.encode(prepared) ?: return null
        val constraint = spellings?.let { GlideSpellingTrie(it) } ?: OpenGlideSpellingConstraint
        val hypotheses = CtcTrieBeam.decode(emissions, constraint, limit, CtcTrieBeam.Params())
        if (BuildConfig.DEBUG) {
            Log.d(
                "FutoGlide",
                "points=" + points.size + " keys=" + prepared.keyCount +
                    " hyps=" + hypotheses.size + " top=" + hypotheses.firstOrNull()?.letters +
                    " ms=" + (SystemClock.elapsedRealtime() - start),
            )
        }
        return hypotheses.ifEmpty { null }
    }
}
