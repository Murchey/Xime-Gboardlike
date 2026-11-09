package com.kingzcheung.xime.objectstorage

/**
 * 对象存储协议。
 *
 * 三家签名方式不同，但上层（剪切板同步 / 备份）只看到统一的
 * [ObjectStorageClient] 接口，不感知协议差异。
 */
enum class StorageProtocol(val prefValue: String) {
    /** 腾讯云 COS：q-sign-algorithm=sha1 */
    COS("cos"),

    /** 阿里云 OSS：Authorization: OSS AccessKeyId:Signature（HMAC-SHA1） */
    OSS("oss"),

    /** AWS S3 / MinIO / 自建 S3：Signature V4（HMAC-SHA256） */
    S3("s3"),
    ;

    companion object {
        fun fromPrefValue(value: String?): StorageProtocol? =
            entries.firstOrNull { it.prefValue == value }
    }
}

/**
 * 对象存储桶配置（剪切板同步与数据备份共用一套）。
 *
 * 凭据 [secretKey] 经 [com.kingzcheung.xime.plugin.SecureValueCipher] 加密后落盘，
 * 明文只存在于内存与请求签名的瞬间；备份导出时按 `os_sk` 键脱敏（见 BackupManager）。
 *
 * @param privateAuth 私有读写（默认、推荐）：所有请求都签名；公有读写时**仍保留
 *   key/id 但不做签名**（读取可匿名，写入能否成功取决于桶策略）。
 */
data class ObjectStorageConfig(
    val protocol: StorageProtocol = StorageProtocol.COS,
    /** 桶访问域名或 Endpoint，如 xxx-1250000000.cos.ap-guangzhou.myqcloud.com */
    val endpoint: String = "",
    /** Bucket 名（S3 路径式访问必填；COS/OSS 使用 host 中的桶）。 */
    val bucket: String = "",
    /** AccessKey ID / SecretId / AccessKey */
    val accessKeyId: String = "",
    /** SecretKey（加密存储） */
    val secretKey: String = "",
    val privateAuth: Boolean = true,
) {
    /** 去掉协议前缀与尾斜杠后的主机部分。 */
    val host: String
        get() = endpoint.trim().trimEnd('/')
            .removePrefix("https://").removePrefix("http://")
            .substringBefore("/").substringBefore("?").substringBefore("#")

    /** 用于状态展示的短标识。 */
    val bucketLabel: String
        get() = bucket.trim().ifBlank {
            host.substringBefore(".cos.").substringBefore(".oss-").ifBlank { host }
        }

    /** 是否具备发起请求的最低条件（不校验 key 是否真实有效）。 */
    val isConfigured: Boolean
        get() = host.isNotBlank() &&
            (protocol != StorageProtocol.S3 || bucket.isNotBlank()) &&
            (!privateAuth || (accessKeyId.isNotBlank() && secretKey.isNotBlank()))

    /** 是否使用 https（默认）。用户显式写 http 时尊重。 */
    val useHttps: Boolean
        get() = !endpoint.trim().startsWith("http://", ignoreCase = true)

    val scheme: String get() = if (useHttps) "https" else "http"
}
