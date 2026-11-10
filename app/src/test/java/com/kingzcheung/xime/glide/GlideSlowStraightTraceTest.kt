package com.kingzcheung.xime.glide

import org.junit.Assert.*
import org.junit.Test

class GlideSlowStraightTraceTest {
    private fun qwerty(): GlideGeometrySnapshot {
        val w = 100f; val h = 100f
        val keys = "qwertyuiop".mapIndexed { c, ch ->
            GlideKey(ch.toString(), ch.toString(), GlideKeyRect(c * w, 0f, c * w + w, h))
        } + "asdfghjkl".mapIndexed { c, ch ->
            val x = 50f + c * w
            GlideKey(ch.toString(), ch.toString(), GlideKeyRect(x, h, x + w, 2 * h))
        } + "zxcvbnm".mapIndexed { c, ch ->
            val x = 150f + c * w
            GlideKey(ch.toString(), ch.toString(), GlideKeyRect(x, 2 * h, x + w, 3 * h))
        }
        return GlideGeometrySnapshot("slow", keys, w * 11, h * 3)
    }

    private fun slowLine(g: GlideGeometrySnapshot, from: Char, to: Char, pointIntervalMs: Long): List<GlidePoint> {
        val a = g.keys.first { it.code == from.toString() }.rect
        val b = g.keys.first { it.code == to.toString() }.rect
        val ax = a.centerX; val ay = a.centerY; val bx = b.centerX; val by = b.centerY
        val result = mutableListOf<GlidePoint>()
        var t = 0L
        for (step in 0..90) {
            result += GlidePoint(ax + (bx - ax) * step / 90f, ay + (by - ay) * step / 90f, t, 1)
            t += pointIntervalMs
        }
        return result
    }

    @Test fun 慢速横滑只应产生短编码不把途经键全计入() {
        val g = qwerty()
        val fast = GlideGeometryDecoder.decode(slowLine(g, 'q', 'o', 16), g, 16)
        val slow = GlideGeometryDecoder.decode(slowLine(g, 'q', 'o', 60), g, 16)
        assertTrue("慢速不应产生更长串: " + slow.take(3),
            slow.first().letters.length <= fast.first().letters.length + 1)
        assertTrue("慢速应选短编码: " + slow.first().letters,
            slow.first().letters.length <= 4)
        assertFalse("慢速不应把沿途键连成 aeertuioo 之类长串: " + slow.map { it.letters },
            slow.any { it.letters.length >= 8 })
    }
}
