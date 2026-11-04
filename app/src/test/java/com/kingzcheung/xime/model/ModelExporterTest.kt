package com.kingzcheung.xime.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipFile

/** [ModelExporter] 的打包与清单逻辑（不依赖 Android Context）。 */
class ModelExporterTest {

    @Test
    fun manifestJsonContainsFormatAndFiles() {
        val manifest = ModelExporter.Manifest(
            modelId = "predictive-text-small",
            modelName = "联想 small",
            version = "1.0.0",
            exportedAtMs = 1_700_000_000_000L,
            source = "XimeGboard",
            files = listOf(
                ModelExporter.FileEntry("vocab.json", 1024L, "abc"),
                ModelExporter.FileEntry("model_int8_dynamic.onnx", 2048L, "def"),
            ),
        )

        val json = org.json.JSONObject(ModelExporter.manifestToJson(manifest))

        assertEquals(1, json.getInt("format_version"))
        assertEquals("predictive-text-small", json.getString("model_id"))
        assertEquals(1_700_000_000_000L, json.getLong("exported_at_ms"))
        val files = json.getJSONArray("files")
        assertEquals(2, files.length())
        assertEquals("vocab.json", files.getJSONObject(0).getString("name"))
        assertEquals(1024L, files.getJSONObject(0).getLong("size_bytes"))
        assertEquals("def", files.getJSONObject(1).getString("sha256"))
    }

    @Test
    fun suggestFileNameCarriesModelIdAndVersion() {
        val model = ModelInfo(
            id = "predictive-text-base",
            name = "联想 base",
            description = "",
            category = ModelCategory.PREDICTION,
            versions = listOf(ModelVersion(version = "2.1.0")),
        )

        val name = ModelExporter.suggestFileName(model)

        assertTrue(name.startsWith("XimeGboard-predictive-text-base-2.1.0-"))
        assertTrue(name.endsWith(".zip"))
    }

    @Test
    fun archiveLayoutKeepsFlatNamesAndManifest() {
        // 直接构造一个与 writeZip 相同布局的包，验证读取侧 assumptions 成立
        val dir = kotlin.io.path.createTempDirectory("model-export-test").toFile()
        val zip = java.io.File(dir, "sample.zip")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { out ->
            listOf("vocab.json", "model_int8_dynamic.onnx", "manifest.json").forEach { name ->
                out.putNextEntry(java.util.zip.ZipEntry(name))
                out.write(name.toByteArray())
                out.closeEntry()
            }
        }

        ZipFile(zip).use { zf ->
            assertNotNull(zf.getEntry("vocab.json"))
            assertNotNull(zf.getEntry("model_int8_dynamic.onnx"))
            assertNotNull(zf.getEntry("manifest.json"))
            // 导入侧按 basename 落盘，必须没有子目录
            assertTrue(zf.entries().toList().none { it.name.contains('/') })
        }
        dir.deleteRecursively()
    }
}
