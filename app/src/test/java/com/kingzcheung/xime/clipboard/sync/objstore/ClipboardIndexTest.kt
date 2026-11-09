package com.kingzcheung.xime.clipboard.sync.objstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 索引哈希、JSON 往返、双向合并 / 增量推送 / 墓碑删除的纯函数单测。 */
class ClipboardIndexTest {

    private fun item(uuid: String, sha: String, updated: Long, type: String = "text") =
        ClipboardIndexItem(uuid, sha, updated, type)

    // ── 哈希 / 规范化 ──

    @Test
    fun `规范化清单按 uuid 排序`() {
        val a = ClipboardIndexCalculator.canonicalItemsJson(listOf(item("b", "s2", 2), item("a", "s1", 1)))
        val b = ClipboardIndexCalculator.canonicalItemsJson(listOf(item("a", "s1", 1), item("b", "s2", 2)))
        assertEquals(a, b)
    }

    @Test
    fun `build 产出 64 位 sha256 且条目已合并`() {
        val idx = ClipboardIndexCalculator.build(
            listOf(item("a", "s1", 1), item("a", "s2", 2), item("b", "s3", 3)),
            now = 100L,
        )
        assertEquals(64, idx.sha256.length)
        assertEquals(2, idx.items.size)
        assertEquals("s2", idx.items.first { it.uuid == "a" }.sha256)
        assertEquals(100L, idx.updatedAt)
    }

    @Test
    fun `内容不变则哈希不变`() {
        val items = listOf(item("a", "s1", 1), item("b", "s2", 2))
        assertEquals(
            ClipboardIndexCalculator.build(items, now = 1).sha256,
            ClipboardIndexCalculator.build(items, now = 999).sha256,
        )
    }

    @Test
    fun `内容变化则哈希变化`() {
        val a = ClipboardIndexCalculator.build(listOf(item("a", "s1", 1)), now = 1)
        val b = ClipboardIndexCalculator.build(listOf(item("a", "s2", 2)), now = 1)
        assertTrue(a.sha256 != b.sha256)
    }

    // ── JSON 往返 ──

    @Test
    fun `index JSON 往返保留 items 与 tombstones`() {
        val idx = ClipboardIndex(
            updatedAt = 123L,
            sha256 = "abc",
            items = listOf(item("u1", "h1", 10), item("u2", "h2", 20, "image")),
            tombstones = listOf(ClipboardTombstone("u3", 30)),
        )
        val restored = ClipboardIndex.fromJson(idx.toJson().toString())
        assertEquals(123L, restored.updatedAt)
        assertEquals("abc", restored.sha256)
        assertEquals(2, restored.items.size)
        assertEquals("image", restored.items.first { it.uuid == "u2" }.type)
        assertEquals(1, restored.tombstones.size)
        assertEquals("u3", restored.tombstones[0].uuid)
    }

    @Test
    fun `畸形 JSON 回退为空索引`() {
        val idx = ClipboardIndex.fromJson("not-json")
        assertTrue(idx.items.isEmpty())
        assertEquals("", idx.sha256)
    }

    @Test
    fun `缺字段的 item 被跳过`() {
        val idx = ClipboardIndex.fromJson("""{"items":[{"sha256":"x"},{"uuid":"ok","sha256":"y","updatedAt":1}]}""")
        assertEquals(1, idx.items.size)
        assertEquals("ok", idx.items[0].uuid)
    }

    // ── 双向合并 ──

    @Test
    fun `远端新增被下载`() {
        val local = ClipboardIndex(items = listOf(item("a", "s1", 1)))
        val remote = ClipboardIndex(items = listOf(item("a", "s1", 1), item("b", "s2", 2)))
        val plan = ClipboardIndexCalculator.merge(local, remote, now = 10)
        assertEquals(listOf("b"), plan.toDownload)
        assertTrue(plan.toUpload.isEmpty())
        assertEquals(2, plan.mergedItems.size)
    }

    @Test
    fun `本机新增被上传`() {
        val local = ClipboardIndex(items = listOf(item("a", "s1", 1), item("c", "s3", 3)))
        val remote = ClipboardIndex(items = listOf(item("a", "s1", 1)))
        val plan = ClipboardIndexCalculator.merge(local, remote, now = 10)
        assertEquals(listOf("c"), plan.toUpload)
        assertTrue(plan.toDownload.isEmpty())
    }

    @Test
    fun `同 uuid 冲突取 updatedAt 较新者`() {
        val local = ClipboardIndex(items = listOf(item("a", "local", 1)))
        val remote = ClipboardIndex(items = listOf(item("a", "remote", 5)))
        val plan = ClipboardIndexCalculator.merge(local, remote, now = 10)
        assertEquals(listOf("a"), plan.toDownload)
        assertTrue(plan.toUpload.isEmpty())
        assertEquals("remote", plan.mergedItems.single().sha256)
    }

    @Test
    fun `本机较新则上传`() {
        val local = ClipboardIndex(items = listOf(item("a", "local", 9)))
        val remote = ClipboardIndex(items = listOf(item("a", "remote", 5)))
        val plan = ClipboardIndexCalculator.merge(local, remote, now = 10)
        assertEquals(listOf("a"), plan.toUpload)
        assertTrue(plan.toDownload.isEmpty())
    }

    @Test
    fun `内容一致不产生传输`() {
        val both = ClipboardIndex(items = listOf(item("a", "s1", 1)))
        val plan = ClipboardIndexCalculator.merge(both, both.copy(updatedAt = 99), now = 10)
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `墓碑导致远端对象被删除且不复活`() {
        val remote = ClipboardIndex(items = listOf(item("a", "s1", 1), item("b", "s2", 2)))
        val local = ClipboardIndex(
            items = listOf(item("b", "s2", 2)),
            tombstones = listOf(ClipboardTombstone("a", 5)),
        )
        val plan = ClipboardIndexCalculator.merge(local, remote, now = 10)
        assertEquals(listOf("a"), plan.toDelete)
        assertFalse(plan.mergedItems.any { it.uuid == "a" })
        assertTrue(plan.toDownload.isEmpty())
    }

    @Test
    fun `远端墓碑也会删除本机对象`() {
        val local = ClipboardIndex(items = listOf(item("a", "s1", 1)))
        val remote = ClipboardIndex(
            items = listOf(item("a", "s1", 1)),
            tombstones = listOf(ClipboardTombstone("a", 5)),
        )
        val plan = ClipboardIndexCalculator.merge(local, remote, now = 10)
        assertEquals(listOf("a"), plan.toDelete)
        assertFalse(plan.mergedItems.any { it.uuid == "a" })
    }

    @Test
    fun `双端墓碑合并去重`() {
        val local = ClipboardIndex(tombstones = listOf(ClipboardTombstone("a", 1)))
        val remote = ClipboardIndex(tombstones = listOf(ClipboardTombstone("a", 9), ClipboardTombstone("b", 2)))
        val plan = ClipboardIndexCalculator.merge(local, remote, now = 10)
        assertEquals(2, plan.mergedTombstones.size)
        assertEquals(9L, plan.mergedTombstones.first { it.uuid == "a" }.deletedAt)
    }

    // ── 增量推送 ──

    @Test
    fun `pushOnly 只上传不下载`() {
        val local = ClipboardIndex(items = listOf(item("a", "s1", 1), item("c", "s3", 3)))
        val remote = ClipboardIndex(items = listOf(item("a", "s1", 1), item("b", "s2", 2)))
        val plan = ClipboardIndexCalculator.pushOnly(local, remote, now = 10)
        assertEquals(listOf("c"), plan.toUpload)
        assertTrue(plan.toDownload.isEmpty())
        // 远端独有的 b 不被删除（本机为主上传，不动远端），也不下载
        assertTrue(plan.toDelete.isEmpty())
    }

    @Test
    fun `pushOnly 本地删除减量到桶`() {
        val local = ClipboardIndex(
            items = listOf(item("b", "s2", 2)),
            tombstones = listOf(ClipboardTombstone("a", 5)),
        )
        val remote = ClipboardIndex(items = listOf(item("a", "s1", 1), item("b", "s2", 2)))
        val plan = ClipboardIndexCalculator.pushOnly(local, remote, now = 10)
        assertEquals(listOf("a"), plan.toDelete)
        assertFalse(plan.mergedItems.any { it.uuid == "a" })
    }

    @Test
    fun `pushOnly 内容变更才上传`() {
        val local = ClipboardIndex(items = listOf(item("a", "new", 5)))
        val remote = ClipboardIndex(items = listOf(item("a", "old", 1)))
        val plan = ClipboardIndexCalculator.pushOnly(local, remote, now = 10)
        assertEquals(listOf("a"), plan.toUpload)
    }

    @Test
    fun `pushOnly 远端较新也仍以本机为主上传`() {
        val local = ClipboardIndex(items = listOf(item("a", "local", 1)))
        val remote = ClipboardIndex(items = listOf(item("a", "remote", 9)))
        val plan = ClipboardIndexCalculator.pushOnly(local, remote, now = 10)
        // 本机为主：sha 不同即上传（不拉远端）
        assertEquals(listOf("a"), plan.toUpload)
        assertTrue(plan.toDownload.isEmpty())
    }
}
