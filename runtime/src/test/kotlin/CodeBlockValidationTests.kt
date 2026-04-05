import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.BoolBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.StringValueBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.EventOutputLocalVariableBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.GetGlobalVarBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.GetVarBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.SetGlobalVarBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.SetVarBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.validation.CodeBlockValidationReporter
import ru.hollowhorizon.hollowengine.common.codeblocks.validation.CodeBlockValidator
import ru.hollowhorizon.hollowengine.common.codeblocks.validation.ValidationIssue
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
@SerialName("test:event")
private class TestEventStartBlock : StartBlock() {
    override val color: Color get() = Color.WHITE
    private val payloadOutput by outputDefault<String>(
        name = PAYLOAD_OUTPUT,
        default = { EventOutputLocalVariableBlock("payload") },
    )

    override suspend fun trigger() {
        payloadOutput.emit("payload")
    }

    override fun InputSlotScope.composeContent() = Unit

    companion object {
        const val PAYLOAD_OUTPUT = "payloadOutput"
    }
}

@Serializable
@SerialName("test:plain")
private class PlainStartBlock : StartBlock() {
    override val color: Color get() = Color.WHITE
    override suspend fun trigger() = Unit
    override fun InputSlotScope.composeContent() = Unit
}

class CodeBlockValidationTests {
    private val validator = CodeBlockValidator()

    @Test
    fun `event output is valid when attached to typed output slot`() {
        val start = TestEventStartBlock()
        start.applyDefaults()

        val issues = validator.validate(listOf(start))

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `detached event output produces validation issue`() {
        val start = PlainStartBlock()
        val expression = EventOutputLocalVariableBlock("payload")
        start.attachInput("value", expression)

        val issues = validator.validate(listOf(start))

        assertEquals(1, issues.size)
        assertTrue(issues.single().message.contains("typed output slot", ignoreCase = true))
    }

    @Test
    fun `conflicting global types across roots produce validation issue`() {
        val first = PlainStartBlock().apply {
            next = SetGlobalVarBlock("shared").also {
                it.parent = this
                it.attachInput("value", StringValueBlock("hello"))
            }
        }
        val second = PlainStartBlock().apply {
            next = SetGlobalVarBlock("shared").also {
                it.parent = this
                it.attachInput("value", BoolBlock(true))
            }
        }

        val issues = validator.validate(listOf(first, second))

        assertEquals(1, issues.size)
        assertTrue(issues.single().message.contains("Conflicting global variable type", ignoreCase = true))
    }

    @Test
    fun `same local variable name in different roots may have different types`() {
        val first = PlainStartBlock().apply {
            next = SetVarBlock("value").also {
                it.parent = this
                it.attachInput("value", StringValueBlock("hello"))
            }
        }
        val second = PlainStartBlock().apply {
            next = SetVarBlock("value").also {
                it.parent = this
                it.attachInput("value", BoolBlock(true))
            }
        }

        val issues = validator.validate(listOf(first, second))

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `conflicting local types in one root produce validation issue`() {
        val start = PlainStartBlock()
        val first = SetVarBlock("value")
        val second = SetVarBlock("value")
        start.next = first
        first.parent = start
        first.attachInput("value", StringValueBlock("hello"))
        first.next = second
        second.parent = first
        second.attachInput("value", BoolBlock(true))

        val issues = validator.validate(listOf(start))

        assertEquals(1, issues.size)
        assertTrue(issues.single().message.contains("Conflicting local variable type", ignoreCase = true))
    }

    @Test
    fun `unknown local and global getters produce validation issues`() {
        val start = PlainStartBlock().apply {
            attachInput("local", GetVarBlock("missingLocal"))
            attachInput("global", GetGlobalVarBlock("missingGlobal"))
        }

        val issues = validator.validate(listOf(start))

        assertEquals(2, issues.size)
        assertTrue(issues.any { it.message.contains("missingLocal") })
        assertTrue(issues.any { it.message.contains("missingGlobal") })
    }

    @Test
    fun `validation reporter writes issues to log file`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val logFile = tempDir.resolve("codeblocks-validation.log")

            CodeBlockValidationReporter.writeReport(
                logFile,
                listOf(ValidationIssue("abc", "Unknown local variable 'player'.", "scripts/test.bc"))
            )

            assertTrue(logFile.exists())
            val contents = logFile.readText()
            assertTrue(contents.contains("Code block analysis issues: 1"))
            assertTrue(contents.contains("scripts/test.bc: Unknown local variable 'player'. [abc]"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `validation reporter removes stale log when issues are cleared`() {
        val tempDir = createTempDirectory().toFile()
        try {
            val logFile = tempDir.resolve("codeblocks-validation.log")
            logFile.writeText("stale")

            CodeBlockValidationReporter.writeReport(logFile, emptyList())

            assertTrue(!logFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
