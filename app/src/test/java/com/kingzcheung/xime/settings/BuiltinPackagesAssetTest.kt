package com.kingzcheung.xime.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 内置资产判定。
 *
 * 回归点：`allAssetNames` 曾返回带 `rime/` 前缀的路径，而注册表里是相对 rime
 * 目录的路径，两者一个都匹配不上——`releaseMisclaimedBuiltinFiles` 于是把
 * **整个注册表清空**（真机实测，179 个文件的归属全丢）。
 */
class BuiltinPackagesAssetTest {

    private val assets = setOf(
        "symbols.yaml",
        "default.yaml",
        "cn_dicts/base.dict.yaml",
        "lua/t9.lua",
        "rime_ice.schema.yaml",
    )

    @Test
    fun `内置文件被认出`() {
        assertTrue(BuiltinPackages.isBuiltinAsset("symbols.yaml", assets))
        assertTrue(BuiltinPackages.isBuiltinAsset("cn_dicts/base.dict.yaml", assets))
        assertTrue(BuiltinPackages.isBuiltinAsset("lua/t9.lua", assets))
    }

    @Test
    fun `用户手拷的第三方方案不是内置资产`() {
        assertFalse(BuiltinPackages.isBuiltinAsset("wanxiang.schema.yaml", assets))
        assertFalse(BuiltinPackages.isBuiltinAsset("wanxiang_algebra.yaml", assets))
        assertFalse(BuiltinPackages.isBuiltinAsset("lua/wanxiang/super_filter.lua", assets))
        assertFalse(BuiltinPackages.isBuiltinAsset("opencc/wanxiang/STPhrases.ocd2", assets))
    }

    @Test
    fun `拿不到资产清单时保守认为属于内置`() {
        // 宁可少清理也不能误删：assets.list 失败时不能返回 false
        assertTrue(BuiltinPackages.isBuiltinAsset("anything.yaml", null))
    }

    @Test
    fun `带 rime 前缀的路径匹配不上`() {
        // 这正是线上那次的 bug 形态：前缀没去掉导致全不匹配
        assertFalse(BuiltinPackages.isBuiltinAsset("rime/symbols.yaml", assets))
    }

    @Test
    fun `空路径不是内置资产`() {
        assertFalse(BuiltinPackages.isBuiltinAsset("", assets))
    }
}
