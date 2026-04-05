import ru.hollowhorizon.hollowengine.common.codeblocks.BlockRepository
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.RecoveryAction
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.ScriptLoadIssue
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.ScriptLoadReport
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.infrastructure.ScriptBackupService
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.usecase.PersistRecoveredScriptUseCase
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScriptRecoveryTests {
    private val format = CodeBlockFormat(BlockRepository.create("Test") {})
    private val issue = ScriptLoadIssue(
        kind = ScriptLoadIssue.Kind.DECODE_FAILED,
        message = "broken script",
        action = RecoveryAction.DROPPED_BLOCK
    )

    @Test
    fun `backup service reuses backup for identical file contents`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val file = tempDir.resolve("script.bc").apply { writeText("broken") }

            val first = ScriptBackupService.createBackup(file)
            val second = ScriptBackupService.createBackup(file)

            assertEquals(first.absolutePath, second.absolutePath)
            assertEquals(1, tempDir.listFiles { candidate -> candidate.name.contains(".backup.") }?.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `persist recovered script skips backup when recovered contents match current file`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val recoveredText = format.encodeBlocks(emptyList())
            val file = tempDir.resolve("script.bc").apply { writeText(recoveredText) }

            val backup = PersistRecoveredScriptUseCase().execute(
                file = file,
                format = format,
                report = ScriptLoadReport(emptyList(), listOf(issue))
            )

            assertNull(backup)
            assertEquals(recoveredText, file.readText())
            assertTrue(tempDir.listFiles { candidate -> candidate.name.contains(".backup.") }.isNullOrEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `persist recovered script creates backup and rewrites broken file`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val file = tempDir.resolve("script.bc").apply { writeText("broken") }
            val recoveredText = format.encodeBlocks(emptyList())

            val backup = PersistRecoveredScriptUseCase().execute(
                file = file,
                format = format,
                report = ScriptLoadReport(emptyList(), listOf(issue))
            )

            assertNotNull(backup)
            assertTrue(backup.exists())
            assertEquals("broken", backup.readText())
            assertEquals(recoveredText, file.readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
