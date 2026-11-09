package com.kingzcheung.xime.speech

import android.content.SharedPreferences
import com.kingzcheung.xime.plugin.ValueCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 可逆的假加密：用来验证"存的是密文、读的是明文"。 */
private class FakeCipher : ValueCipher {
    override fun encrypt(plain: String): String = "enc:" + plain.reversed()
    override fun decrypt(stored: String): String? =
        if (stored.startsWith("enc:")) stored.removePrefix("enc:").reversed() else stored
}

/** 只实现 get/put，其余接口按未使用抛异常——足够覆盖 AsrKeyStore 的全部路径。 */
private class FakePrefs : SharedPreferences {
    val data = mutableMapOf<String, String?>()

    override fun getString(key: String, defValue: String?): String? =
        if (data.containsKey(key)) data[key] else defValue

    override fun edit(): SharedPreferences.Editor = FakeEditor(data)

    override fun getAll(): MutableMap<String, *> = data
    override fun getStringSet(k: String, d: MutableSet<String>?) = d
    override fun getInt(k: String, d: Int) = d
    override fun getLong(k: String, d: Long) = d
    override fun getFloat(k: String, d: Float) = d
    override fun getBoolean(k: String, d: Boolean) = d
    override fun contains(k: String) = data.containsKey(k)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private class FakeEditor(private val data: MutableMap<String, String?>) : SharedPreferences.Editor {
        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { data[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { data.remove(key) }
        override fun clear(): SharedPreferences.Editor = apply { data.clear() }
        override fun commit(): Boolean = true
        override fun apply() = Unit
        override fun putStringSet(k: String, v: MutableSet<String>?) = this
        override fun putInt(k: String, v: Int) = this
        override fun putLong(k: String, v: Long) = this
        override fun putFloat(k: String, v: Float) = this
        override fun putBoolean(k: String, v: Boolean) = this
    }
}

class AsrKeyStoreTest {

    private lateinit var prefs: FakePrefs

    private fun store(cipher: ValueCipher = FakeCipher()) = AsrKeyStore(prefs, cipher)

    @org.junit.Before
    fun setUp() {
        prefs = FakePrefs()
    }

    @Test
    fun `写入后取回明文`() {
        val subject = store()
        subject.set("openai", "sk-123")
        assertEquals("sk-123", subject.get("openai"))
        assertTrue(subject.has("openai"))
    }

    @Test
    fun `落盘的是密文而不是明文`() {
        store().set("openai", "sk-123")
        val raw = prefs.data["asr_key_enc_openai"]
        assertTrue(raw != null)
        assertFalse(raw!!.contains("sk-123"))
    }

    @Test
    fun `历史明文自动迁移并删除`() {
        prefs.data["asr_key_openai"] = "legacy-key"
        val subject = store()
        assertEquals("legacy-key", subject.get("openai"))
        assertNull(prefs.data["asr_key_openai"])
        assertTrue(prefs.data.containsKey("asr_key_enc_openai"))
    }

    @Test
    fun `空值等价于未配置`() {
        val subject = store()
        subject.set("openai", "   ")
        assertEquals("", subject.get("openai"))
        assertFalse(subject.has("openai"))
    }

    @Test
    fun `清除会同时删掉明文与密文键`() {
        prefs.data["asr_key_openai"] = "legacy"
        prefs.data["asr_key_enc_openai"] = "enc:abc"
        store().clear("openai")
        assertTrue(prefs.data.isEmpty())
    }

    @Test
    fun `密文损坏时返回空串而不崩溃`() {
        prefs.data["asr_key_enc_openai"] = "enc:xxx"
        val broken = object : ValueCipher {
            override fun encrypt(plain: String): String = "enc:" + plain
            override fun decrypt(stored: String): String? = null
        }
        assertEquals("", store(broken).get("openai"))
    }

    @Test
    fun `不同服务商的 Key 互不覆盖`() {
        val subject = store()
        subject.set("openai", "k1")
        subject.set("mimo", "k2")
        assertEquals("k1", subject.get("openai"))
        assertEquals("k2", subject.get("mimo"))
    }

    @Test
    fun `Key 前后空白被去除`() {
        val subject = store()
        subject.set("openai", "  sk-1  ")
        assertEquals("sk-1", subject.get("openai"))
    }
}
