package com.kingzcheung.xime.objectstorage

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.plugin.SecureValueCipher
import com.kingzcheung.xime.plugin.ValueCipher
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 对象存储配置的持久化。
 *
 * SecretKey 经 [SecureValueCipher]（Android Keystore AES-GCM）加密后写入
 * `kime_settings`；旧版本若有明文 `os_sk` 则读取时自动迁移并删除。
 * 备份导出/恢复侧的脱敏由 `BackupManager.isSensitivePrefKey` 负责（匹配 os_sk）。
 */
class ObjectStorageConfigStore(
    private val prefs: SharedPreferences,
    private val cipher: ValueCipher = SecureValueCipher,
) {
    companion object {
        const val KEY_PROTOCOL = "os_protocol"
        const val KEY_ENDPOINT = "os_endpoint"
        const val KEY_BUCKET = "os_bucket"
        const val KEY_AK = "os_ak"
        const val KEY_SK = "os_sk"
        internal const val KEY_SK_ENC = "os_sk_enc"
        const val KEY_PRIVATE = "os_private"

        fun of(context: Context): ObjectStorageConfigStore =
            ObjectStorageConfigStore(SettingsPreferences.getPrefsPublic(context))
    }

    fun load(): ObjectStorageConfig {
        val sk = readSecretKey()
        return ObjectStorageConfig(
            protocol = StorageProtocol.fromPrefValue(prefs.getString(KEY_PROTOCOL, null))
                ?: StorageProtocol.COS,
            endpoint = prefs.getString(KEY_ENDPOINT, "").orEmpty(),
            bucket = prefs.getString(KEY_BUCKET, "").orEmpty(),
            accessKeyId = prefs.getString(KEY_AK, "").orEmpty(),
            secretKey = sk,
            privateAuth = prefs.getBoolean(KEY_PRIVATE, true),
        )
    }

    fun save(config: ObjectStorageConfig) {
        val editor = prefs.edit()
            .putString(KEY_PROTOCOL, config.protocol.prefValue)
            .putString(KEY_ENDPOINT, config.endpoint.trim())
            .putString(KEY_BUCKET, config.bucket.trim())
            .putString(KEY_AK, config.accessKeyId.trim())
            .putBoolean(KEY_PRIVATE, config.privateAuth)
        val sk = config.secretKey.trim()
        if (sk.isEmpty()) {
            editor.remove(KEY_SK_ENC).remove(KEY_SK)
        } else {
            editor.putString(KEY_SK_ENC, cipher.encrypt(sk)).remove(KEY_SK)
        }
        editor.apply()
    }

    private fun readSecretKey(): String {
        val encrypted = prefs.getString(KEY_SK_ENC, null)
        if (!encrypted.isNullOrEmpty()) return cipher.decrypt(encrypted).orEmpty()
        val legacy = prefs.getString(KEY_SK, null)
        if (legacy.isNullOrBlank()) return ""
        // 迁移历史明文
        prefs.edit()
            .putString(KEY_SK_ENC, cipher.encrypt(legacy.trim()))
            .remove(KEY_SK)
            .apply()
        return legacy.trim()
    }
}
