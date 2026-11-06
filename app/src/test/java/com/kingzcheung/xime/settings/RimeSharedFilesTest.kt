package com.kingzcheung.xime.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置共享文件判定：
 * - assets 顶层的目录（如 `lua`）、隐藏项、方案/词典/补丁文件都不是共享文件
 *   （曾把目录 `lua` 当文件复制，部署时抛 FileNotFoundException）
 * - 预设/词表类文件（symbols.yaml、default.yaml、essay.txt 等）是
 */
class RimeSharedFilesTest {

    @Test
    fun `目录不算共享文件`() {
        assertFalse(RimeSharedFiles.isSharedEntry("lua", isDirectory = true))
        assertFalse(RimeSharedFiles.isSharedEntry(".github", isDirectory = true))
    }

    @Test
    fun `隐藏文件不算共享文件`() {
        assertFalse(RimeSharedFiles.isSharedEntry(".git", isDirectory = false))
    }

    @Test
    fun `方案与词典文件不算共享文件`() {
        assertFalse(RimeSharedFiles.isSharedEntry("wubi86.schema.yaml", isDirectory = false))
        assertFalse(RimeSharedFiles.isSharedEntry("wubi86.dict.yaml", isDirectory = false))
        assertFalse(RimeSharedFiles.isSharedEntry("default.custom.yaml", isDirectory = false))
    }

    @Test
    fun `被 __include 引用的预设方案也算共享文件`() {
        // t9.schema.yaml 依赖 __include: rime_ice.schema.yaml；丢了它九键就编译不出来
        assertTrue(RimeSharedFiles.isSharedEntry("rime_ice.schema.yaml", isDirectory = false))
        assertTrue(RimeSharedFiles.isSharedEntry("rime_ice.dict.yaml", isDirectory = false))
        // 其它方案文件仍不算共享，避免把用户方案当内置资源回滚
        assertFalse(RimeSharedFiles.isSharedEntry("t9.schema.yaml", isDirectory = false))
        assertFalse(RimeSharedFiles.isSharedEntry("rime_frost.schema.yaml", isDirectory = false))
    }

    @Test
    fun `预设与词表文件是共享文件`() {
        assertTrue(RimeSharedFiles.isSharedEntry("symbols.yaml", isDirectory = false))
        assertTrue(RimeSharedFiles.isSharedEntry("default.yaml", isDirectory = false))
        assertTrue(RimeSharedFiles.isSharedEntry("essay.txt", isDirectory = false))
    }

    @Test
    fun `文档类文件不算共享文件`() {
        assertFalse(RimeSharedFiles.isSharedEntry("README.md", isDirectory = false))
        assertFalse(RimeSharedFiles.isSharedEntry("LICENSE", isDirectory = false))
    }
}
