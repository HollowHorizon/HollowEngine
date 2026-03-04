package ru.hollowhorizon.hollowengine.common.codeblocks.recovery.usecase

import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.ScriptLoadReport
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.infrastructure.ScriptBackupService
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import java.io.File

class PersistRecoveredScriptUseCase(
    private val backupService: ScriptBackupService = ScriptBackupService,
) {
    fun execute(file: File, format: CodeBlockFormat, report: ScriptLoadReport): File? {
        if (!report.hasIssues) return null
        if (!file.exists()) return null

        val backup = backupService.createBackup(file)
        val temp = file.parentFile.resolve(file.name + ".tmp")
        temp.writeText(format.encodeBlocks(report.blocks))
        temp.copyTo(file, overwrite = true)
        temp.delete()
        return backup
    }
}

