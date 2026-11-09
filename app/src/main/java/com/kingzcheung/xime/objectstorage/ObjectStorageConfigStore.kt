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

    sealed class SaveResult {
        data object Success : SaveResult()
        data object EncryptionFailure : SaveResult()
        data object PersistenceFailure : SaveResult()
    }

    @Synchronized
    fun save(config: ObjectStorageConfig): SaveResult {
        // 加密失败时不创建编辑器，避免旧配置被部分覆盖。
        val sk = config.secretKey.trim()
        val encrypted = try {
            if (sk.isEmpty()) null else cipher.encrypt(sk).also { check(it.isNotEmpty()) }
        } catch (_: Exception) {
            return SaveResult.EncryptionFailure
        }
        val keys = setOf(KEY_PROTOCOL, KEY_ENDPOINT, KEY_BUCKET, KEY_AK, KEY_PRIVATE, KEY_SK, KEY_SK_ENC)
        val previous = prefs.all.filterKeys { it in keys }
        val editor = prefs.edit()
            .putString(KEY_PROTOCOL, config.protocol.prefValue)
            .putString(KEY_ENDPOINT, config.endpoint.trim())
            .putString(KEY_BUCKET, config.bucket.trim())
            .putString(KEY_AK, config.accessKeyId.trim())
            .putBoolean(KEY_PRIVATE, config.privateAuth)
        if (encrypted == null) editor.remove(KEY_SK_ENC).remove(KEY_SK)
        else editor.putString(KEY_SK_ENC, encrypted).remove(KEY_SK)
        val committed = try { editor.commit() } catch (_: Exception) { false }
        if (committed) return SaveResult.Success
        // commit 失败仍可能更新内存；恢复本次涉及的键，其他偏好不受影响。
        runCatching {
            val rollback = prefs.edit()
            keys.forEach { key ->
                when (val value = previous[key]) {
                    is String -> rollback.putString(key, value)
                    is Boolean -> rollback.putBoolean(key, value)
                    else -> rollback.remove(key)
                }
            }
            rollback.commit()
        }
        return SaveResult.PersistenceFailure
    }

    private fun readSecretKey(): String {
        val encrypted = prefs.getString(KEY_SK_ENC, null)
        if (!encrypted.isNullOrEmpty()) return cipher.decrypt(encrypted).orEmpty()
        val legacy = prefs.getString(KEY_SK, null)
        if (legacy.isNullOrBlank()) return ""
        // 迁移历史明文
        runCatching {
            val encryptedLegacy = cipher.encrypt(legacy.trim())
            check(encryptedLegacy.isNotEmpty())
            prefs.edit()
                .putString(KEY_SK_ENC, encryptedLegacy)
                .remove(KEY_SK)
                .commit()
        }
        return legacy.trim()
    }
}
