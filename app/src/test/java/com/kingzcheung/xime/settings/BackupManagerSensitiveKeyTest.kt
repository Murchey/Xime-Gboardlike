package com.kingzcheung.xime.settings

import android.content.SharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 只实现 all，足够覆盖备份导出过滤逻辑。 */
private class AllPrefs(private val entries: Map<String, Any>) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = entries.toMutableMap()
    override fun getString(k: String, d: String?) = entries[k] as? String ?: d
    override fun getStringSet(k: String, d: MutableSet<String>?) = d
    override fun getInt(k: String, d: Int) = entries[k] as? Int ?: d
    override fun getLong(k: String, d: Long) = entries[k] as? Long ?: d
    override fun getFloat(k: String, d: Float) = entries[k] as? Float ?: d
    override fun getBoolean(k: String, d: Boolean) = entries[k] as? Boolean ?: d
    override fun contains(k: String) = entries.containsKey(k)
    override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException("只读测试用")
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

/** 备份包不得携带 API Key：导出侧跳过、恢复侧也跳过旧包里的明文。 */
class BackupManagerSensitiveKeyTest {

    @Test
    fun `识别 Key 前缀被判为敏感`() {
        assertTrue(BackupManager.isSensitivePrefKey("asr_key_openai"))
        assertTrue(BackupManager.isSensitivePrefKey("asr_key_enc_mimo"))
    }

    @Test
    fun `普通设置不受影响`() {
        assertFalse(BackupManager.isSensitivePrefKey("stt_engine"))
        assertFalse(BackupManager.isSensitivePrefKey("asr_model_openai"))
        assertFalse(BackupManager.isSensitivePrefKey("asr_base_openai"))
        assertFalse(BackupManager.isSensitivePrefKey(""))
    }

    @Test
    fun `导出设置时过滤掉 Key`() {
        val prefs = AllPrefs(
            mapOf(
                "stt_engine" to "builtin",
                "asr_key_enc_openai" to "enc:abc",
                "asr_key_minimax" to "legacy",
                "asr_model_openai" to "whisper-1",
            )
        )
        val json = BackupManager.prefsToJson(prefs)
        assertTrue(json.contains("stt_engine"))
        assertTrue(json.contains("asr_model_openai"))
        assertFalse(json.contains("asr_key_enc_openai"))
        assertFalse(json.contains("asr_key_minimax"))
        assertFalse(json.contains("enc:abc"))
        assertFalse(json.contains("legacy"))
    }
}
