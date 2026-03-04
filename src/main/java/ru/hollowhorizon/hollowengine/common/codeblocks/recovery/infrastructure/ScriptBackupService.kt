package ru.hollowhorizon.hollowengine.common.codeblocks.recovery.infrastructure

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ScriptBackupService {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    fun createBackup(file: File): File {
        val stamp = LocalDateTime.now().format(formatter)
        val extension = file.extension
        val backupName = if (extension.isNotEmpty()) {
            "${file.nameWithoutExtension}.$stamp.backup.$extension"
        } else {
            "${file.name}.$stamp.backup"
        }
        val backup = file.parentFile.resolve(backupName)
        file.copyTo(backup, overwrite = true)
        return backup
    }
}

