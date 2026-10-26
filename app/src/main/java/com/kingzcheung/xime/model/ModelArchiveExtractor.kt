package com.kingzcheung.xime.model

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream

internal object ModelArchiveExtractor {
    fun extractTarBz2(archive: File, targetDir: File) {
        FileInputStream(archive).use { fis ->
            BufferedInputStream(fis, 65536).use { bis ->
                BZip2CompressorInputStream(bis).use { bzIn ->
                    TarArchiveInputStream(bzIn).use { tar ->
                        var entry = tar.nextEntry
                        while (entry != null) {
                            if (!entry.isDirectory) {
                                val name = entry.name.replace('\\', '/').substringAfterLast('/')
                                if (name.isNotBlank()) FileOutputStream(File(targetDir, name)).use { tar.copyTo(it) }
                            }
                            entry = tar.nextEntry
                        }
                    }
                }
            }
        }
    }
}
