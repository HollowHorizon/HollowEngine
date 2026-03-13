package ru.hollowhorizon.hollowengine.common.codeblocks.validation

import org.apache.logging.log4j.Logger
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import java.io.File

object CodeBlockValidationReporter {
    private const val LOGS_DIR = "logs"
    private const val LOG_FILE_NAME = "codeblocks-validation.log"

    fun report(issues: List<ValidationIssue>, logger: Logger = HollowCore.LOGGER) {
        val logFile = DirectoryManager.HOLLOW_ENGINE.resolve(LOGS_DIR).resolve(LOG_FILE_NAME).toFile()
        val writtenFile = writeReport(logFile, issues)
        if (issues.isEmpty()) return

        logger.error(
            "Code block analysis found {} issue(s). Details written to {}",
            issues.size,
            writtenFile.absolutePath
        )
        issues.forEach { issue ->
            logger.error("[CodeBlocks] {}", formatIssue(issue))
        }
    }

    fun writeReport(logFile: File, issues: List<ValidationIssue>): File {
        if (issues.isEmpty()) {
            if (logFile.exists()) {
                logFile.delete()
            }
            return logFile
        }

        logFile.parentFile?.mkdirs()
        logFile.writeText(buildReport(issues))
        return logFile
    }

    fun buildReport(issues: List<ValidationIssue>): String {
        return buildString {
            appendLine("Code block analysis issues: ${issues.size}")
            appendLine()
            issues.forEach { issue ->
                appendLine(formatIssue(issue))
            }
        }
    }

    fun formatIssue(issue: ValidationIssue): String {
        val prefix = issue.scriptPath?.let { "$it: " } ?: ""
        return "$prefix${issue.message} [${issue.blockId}]"
    }
}
