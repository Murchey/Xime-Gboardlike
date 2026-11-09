package com.kingzcheung.xime.clipboard.sync.objstore

import java.security.MessageDigest

/** SHA-256 工具（纯 JVM，单测可用）。 */
object ObjectStorageHashes {
    fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
