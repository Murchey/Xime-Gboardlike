package com.kingzcheung.xime.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 已导入页计数：修「可安装 -1」——内置包已安装但 downloaded=false 时曾算出负数。 */
class LocalPackageCountTest {

    private fun item(id: String, installed: Boolean, downloaded: Boolean) =
        LocalPackageItem(
            packageId = id,
            displayName = id,
            version = "",
            downloaded = downloaded,
            installed = installed,
        )

    @Test
    fun builtinInstalledIsNotNegativeInstallable() {
        // 内置包：已安装但归档不在 market 目录（downloaded=false）
        val counts = countLocalPackages(listOf(item("builtin_rime_ice", installed = true, downloaded = false)))
        assertEquals(1, counts.total)
        assertEquals(1, counts.installed)
        assertEquals(0, counts.installable)
    }

    @Test
    fun downloadedButNotInstalledCountsAsInstallable() {
        val counts = countLocalPackages(
            listOf(
                item("builtin_rime_ice", installed = true, downloaded = false),
                item("import_1", installed = false, downloaded = true),
            )
        )
        assertEquals(2, counts.total)
        assertEquals(1, counts.installed)
        assertEquals(1, counts.installable)
    }

    @Test
    fun totalAlwaysEqualsInstalledPlusInstallable() {
        val items = listOf(
            item("a", installed = true, downloaded = true),
            item("b", installed = false, downloaded = true),
            item("c", installed = false, downloaded = false),
        )
        val counts = countLocalPackages(items)
        assertEquals(counts.total, counts.installed + counts.installable)
        assertTrue(counts.installable >= 0)
    }

    @Test
    fun emptyListIsAllZero() {
        val counts = countLocalPackages(emptyList())
        assertEquals(0, counts.total)
        assertEquals(0, counts.installed)
        assertEquals(0, counts.installable)
    }
}
