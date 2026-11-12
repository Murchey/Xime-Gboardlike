package com.kingzcheung.xime.glide.neural

import com.kingzcheung.xime.glide.GlideSpellingTrie
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kotlin CTC beam 必须与 tools/futo-eval 的 Python 参考实现逐条一致。 */
class CtcTrieBeamGoldenTest {

    private fun resource(): JSONObject {
        val stream = javaClass.classLoader!!.getResourceAsStream("futo/golden.json")
            ?: error("futo/golden.json missing; run tools/futo-eval/export_golden.py")
        return JSONObject(stream.bufferedReader().readText())
    }

    @Test
    fun matchesPythonReference() {
        val root = resource()
        val cases = root.getJSONArray("cases")
        assertTrue(cases.length() >= 3)
        var covered = 0
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val id = case.getString("id")
            val logEmissions = FloatArray(FutoEmissions.TIMESTEPS * FutoEmissions.CLASSES)
            val rows = case.getJSONArray("logEmissions")
            for (t in 0 until FutoEmissions.TIMESTEPS) {
                val row = rows.getJSONArray(t)
                for (c in 0 until FutoEmissions.CLASSES) {
                    logEmissions[t * FutoEmissions.CLASSES + c] = row.getDouble(c).toFloat()
                }
            }
            val intention = FloatArray(FutoEmissions.TIMESTEPS) {
                case.getJSONArray("intention").getDouble(it).toFloat()
            }
            val alphabet = chars(case.getJSONArray("alphabet"))
            val slotCodes = chars(case.getJSONArray("slotCodes"))
            val spellings = (0 until case.getJSONArray("spellings").length())
                .map { case.getJSONArray("spellings").getString(it) }
            val emissions = FutoEmissions(logEmissions, intention, alphabet, slotCodes)
            val hypotheses = CtcTrieBeam.decode(
                emissions, GlideSpellingTrie(spellings), limit = 64,
            )
            val pythonTop = case.getJSONArray("neuralTop").getJSONObject(0).getString("letters")
            assertEquals("top-1 mismatch for " + id, pythonTop, hypotheses.first().letters)
            val expected = case.getString("expected")
            if (hypotheses.any { it.letters == expected }) covered++
        }
        // 真实 16 条长句上 Python 参考的 top-64 覆盖为 10/16。
        println("futo golden coverage=" + covered + "/" + cases.length())
        assertTrue("coverage regressed: " + covered + "/" + cases.length(), covered >= 10)
    }

    private fun chars(array: org.json.JSONArray): List<Char> =
        (0 until array.length()).map { array.getString(it).first() }
}
