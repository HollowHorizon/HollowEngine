package ru.hollowhorizon.hollowengine.common.codeblocks.recovery.infrastructure

import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ScriptBackupService {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun createBackup(file: File): File {
        val contentHash = file.sha1()
        file.findExistingBackup(contentHash)?.let { return it }

        val stamp = LocalDateTime.now().format(formatter)
        val extension = file.extension
        val backupName = if (extension.isNotEmpty()) {
            "${file.nameWithoutExtension}.$stamp.$contentHash.backup.$extension"
        } else {
            "${file.name}.$stamp.$contentHash.backup"
        }
        val backup = file.parentFile.resolve(backupName)
        file.copyTo(backup, overwrite = true)
        return backup
    }

    private fun File.findExistingBackup(contentHash: String): File? {
        val escapedName = Regex.escape(nameWithoutExtension)
        val escapedExtension = Regex.escape(extension)
        val pattern = if (extension.isNotEmpty()) {
            Regex("^$escapedName\\.\\d{8}-\\d{6}\\.$contentHash\\.backup\\.$escapedExtension$")
        } else {
            Regex("^${Regex.escape(name)}\\.\\d{8}-\\d{6}\\.$contentHash\\.backup$")
        }
        return parentFile
            ?.listFiles()
            ?.firstOrNull { candidate -> candidate.isFile && pattern.matches(candidate.name) }
    }

    private fun File.sha1(): String {
        val digest = MessageDigest.getInstance("SHA-1")
        inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }.take(12)
    }
}
