package com.kingzcheung.xime.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 市场下载后安装的端到端验证：装完必须能被 discoverSchemas 扫到、并进入启用列表，
 * 否则「部署方案」页面看不到它（用户反馈：万象下载安装后在部署方案中不显示）。
 */
@RunWith(AndroidJUnit4::class)
class MarketInstallVisibleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageId = "e2e_market_install"
    private val schemaId = "e2e_demo"

    private val schemaYaml = """
        # Rime schema
        schema:
          schema_id: $schemaId
          name: 端到端测试方案
          version: "1.0"
          author:
            - e2e
          description: |
            仅用于测试安装后是否出现在方案列表。
        speller:
          alphabet: zyxwvutsrqponmlkjihgfedcba
        translator:
          dictionary: $schemaId
    """.trimIndent()

    private val dictYaml = """
        # Rime dictionary
        name: $schemaId
        version: "1.0"
        sort: by_weight
        ...

        测试	ce
    """.trimIndent()

    @Before
    fun prepareArchive() {
        val dir = SchemaManager.getMarketDir(context, packageId)
        dir.deleteRecursively()
        dir.mkdirs()
        val zip = File(dir, "$packageId.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            listOf(
                "$schemaId.schema.yaml" to schemaYaml,
                "$schemaId.dict.yaml" to dictYaml,
            ).forEach { (name, text) ->
                out.putNextEntry(ZipEntry(name))
                out.write(text.toByteArray())
                out.closeEntry()
            }
        }
    }

    @After
    fun cleanup() {
        val rimeDir = SchemaManager.getRimeDir(context)
        File(rimeDir, "$schemaId.schema.yaml").delete()
        File(rimeDir, "$schemaId.dict.yaml").delete()
        SchemaManager.getMarketDir(context, packageId).deleteRecursively()
        runBlocking { SchemaManifestManager.deleteManifest(context, packageId) }
        SchemaManager.setEnabledSchemas(
            context, SchemaManager.getEnabledSchemas(context).filterNot { it == "$schemaId" },
        )
        File(SchemaManager.getBuildDir(context), "$schemaId.schema.yaml").delete()
    }

    @Test
    fun firstInstallIsDiscoverableAndEnabled() = runBlocking {
        val before = SchemaManager.discoverSchemas(context).map { it.schemaId }
        assertTrue("测试方案不应预先存在", "$schemaId" !in before)

        val result = SchemaManager.installPackageFromMarketDir(
            context = context,
            packageId = packageId,
            displayName = "端到端测试包",
            version = "1.0",
            fromMarket = true,
            switchEnabled = true,
        )
        val detail = result.failureReason ?: ""
        assertTrue("安装失败: " + detail, result.success)

        val after = SchemaManager.discoverSchemas(context).map { it.schemaId }
        assertTrue("装完必须能被扫描到（部署方案的数据源）", "$schemaId" in after)
        assertTrue("首次安装必须并入启用列表", "$schemaId" in SchemaManager.getEnabledSchemas(context))
    }
}
