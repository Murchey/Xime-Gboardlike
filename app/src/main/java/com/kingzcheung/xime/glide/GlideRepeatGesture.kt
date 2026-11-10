package com.kingzcheung.xime.glide

import kotlin.math.hypot

/** 同一键内的小幅折返，为连续相同编码提供证据；坐标以键宽/高归一化。 */
internal object GlideRepeatGesture {
    data class Cue(val key: Int, val startArc: Float, val endArc: Float)

    fun detect(points: List<GlidePoint>, centers: List<Pair<Float, Float>>): List<Cue> {
        if (points.size < 3 || centers.isEmpty()) return emptyList()
        fun distance(a: GlidePoint, b: GlidePoint) = hypot(a.x - b.x, a.y - b.y)
        val arc = FloatArray(points.size)
        for (i in 1 until points.size) arc[i] = arc[i - 1] + distance(points[i - 1], points[i])
        val nearest = points.map { p -> centers.indices.minByOrNull { k ->
            val c = centers[k]
            hypot(p.x - c.first, p.y - c.second)
        }!! }
        val cues = mutableListOf<Cue>()
        var start = 0
        while (start < points.lastIndex - 1) {
            val a = points[start]
            val key = nearest[start]
            val center = centers[key]
            if (hypot(a.x - center.first, a.y - center.second) > 0.45f) { start++; continue }
            val startRadius = hypot(a.x - center.first, a.y - center.second)
            var outward = 0f
            var excursion = 0f
            var endFound = -1
            for (end in start + 1..points.lastIndex) {
                val b = points[end]
                if (nearest[end] != key || b.timeMs - a.timeMs > 650L) break
                val d = distance(a, b)
                excursion = maxOf(excursion, d)
                outward = maxOf(outward, hypot(b.x - center.first, b.y - center.second) - startRadius)
                if (outward >= 0.18f && excursion >= 0.22f && d <= 0.12f &&
                    arc[end] - arc[start] >= 0.44f && b.timeMs - a.timeMs >= 32L) {
                    endFound = end
                    break
                }
            }
            if (endFound >= 0) {
                cues += Cue(key, arc[start], arc[endFound])
                start = endFound
            } else start++
        }
        return cues
    }
}
