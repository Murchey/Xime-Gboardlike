package com.kingzcheung.xime.speech

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.plugin.SecureValueCipher
import com.kingzcheung.xime.plugin.ValueCipher
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 内置在线识别的 API Key 存储。
 *
 * Key 属于长期凭据，明文落在 SharedPreferences 里会被备份包带走，因此统一经
 * [SecureValueCipher]（Android Keystore AES-GCM，私钥不可导出）加密后存放。
 *
 * 历史版本把 Key 明文写在 `asr_key_<providerId>`，首次读取时自动迁移到密文键并删除明文。
 */
class AsrKeyStore(
    private val prefs: SharedPreferences,
    private val cipher: ValueCipher = SecureValueCipher,
) {

    companion object {
        private const val ENC_PREFIX = "asr_key_enc_"

        /** 旧版本明文键前缀，仅用于迁移。 */
        internal const val LEGACY_PREFIX = "asr_key_"

        fun of(context: Context): AsrKeyStore =
            AsrKeyStore(SettingsPreferences.getPrefsPublic(context))
    }

    /** 是否已配置 Key（空白视为未配置）。 */
    fun has(providerId: String): Boolean = get(providerId).isNotBlank()

    /** 取明文 Key；未配置或密文不可解（如换机后 Keystore 密钥丢失）时返回空串。 */
    fun get(providerId: String): String {
        val encrypted = prefs.getString(ENC_PREFIX + providerId, null)
        if (!encrypted.isNullOrEmpty()) {
            return cipher.decrypt(encrypted).orEmpty()
        }
        // 迁移历史明文
        val legacy = prefs.getString(LEGACY_PREFIX + providerId, null)
        if (legacy.isNullOrBlank()) return ""
        set(providerId, legacy)
        prefs.edit().remove(LEGACY_PREFIX + providerId).apply()
        return legacy
    }

    /** 写入 Key（自动去除首尾空白）；空串等价于清除。 */
    fun set(providerId: String, key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            clear(providerId)
            return
        }
        prefs.edit().putString(ENC_PREFIX + providerId, cipher.encrypt(trimmed)).apply()
    }

    fun clear(providerId: String) {
        prefs.edit()
            .remove(ENC_PREFIX + providerId)
            .remove(LEGACY_PREFIX + providerId)
            .apply()
    }
}
