import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockRepository
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.BrokenExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.BrokenStatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.DecodeFailureStrategy
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.MissingReferenceStrategy
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.RecoveryAction
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.ScriptLoadIssue
import ru.hollowhorizon.hollowengine.common.codeblocks.recovery.domain.ScriptRecoveryPolicy
import java.io.ByteArrayInputStream
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
@SerialName("test:stmt")
private class RecoveryTestStatementBlock : StatementBlock() {
    override val color: Color get() = Color.WHITE
    override suspend fun execute() = Unit
    override fun InputSlotScope.composeContent() = Unit
}

class CodeBlockRecoveryTests {
    private fun createFormat(policy: ScriptRecoveryPolicy = ScriptRecoveryPolicy.lenient()): CodeBlockFormat {
        val repository = BlockRepository.create("Test") {
            category("Test", null) {
                block("Stmt") { RecoveryTestStatementBlock() }
            }
        }
        return CodeBlockFormat(repository, policy)
    }

    @Test
    fun `missing next reference is replaced with stub without crashing`() {
        val format = createFormat()
        val rootId = UUID.randomUUID()
        val missingId = UUID.randomUUID()

        val json = """
            [
              {
                "node": {
                  "type": "test:stmt",
                  "uuid": "$rootId"
                },
                "next": "$missingId",
                "x": 10.0,
                "y": 20.0,
                "isCollapsed": false
              }
            ]
        """.trimIndent()

        val report = format.loadBlocksWithRecovery(ByteArrayInputStream(json.toByteArray()))
        val root = report.blocks.single() as StatementBlock

        assertTrue(report.hasIssues)
        assertIs<BrokenStatementBlock>(root.next)
        assertEquals(missingId, root.next!!.uuid)
    }

    @Test
    fun `unknown block type is dropped and valid block still loads`() {
        val format = createFormat()
        val badId = UUID.randomUUID()
        val goodId = UUID.randomUUID()

        val json = """
            [
              {
                "node": {
                  "type": "test:unknown",
                  "uuid": "$badId"
                },
                "isCollapsed": false
              },
              {
                "node": {
                  "type": "test:stmt",
                  "uuid": "$goodId"
                },
                "x": 0.0,
                "y": 0.0,
                "isCollapsed": false
              }
            ]
        """.trimIndent()

        val report = format.loadBlocksWithRecovery(ByteArrayInputStream(json.toByteArray()))

        assertTrue(report.hasIssues)
        assertEquals(1, report.blocks.size)
        assertEquals(goodId, report.blocks.single().uuid)
    }

    @Test
    fun `strict mode throws on missing next reference`() {
        val format = createFormat(
            ScriptRecoveryPolicy(
                decodeFailureStrategy = DecodeFailureStrategy.FAIL,
                missingReferenceStrategy = MissingReferenceStrategy.FAIL
            )
        )

        val rootId = UUID.randomUUID()
        val missingId = UUID.randomUUID()

        val json = """
            [
              {
                "node": {
                  "type": "test:stmt",
                  "uuid": "$rootId"
                },
                "next": "$missingId",
                "isCollapsed": false
              }
            ]
        """.trimIndent()

        assertFailsWith<SerializationException> {
            format.loadBlocks(ByteArrayInputStream(json.toByteArray()))
        }
    }

    @Test
    fun `missing reference remove strategy detaches link and records issue`() {
        val format = createFormat(
            ScriptRecoveryPolicy(
                decodeFailureStrategy = DecodeFailureStrategy.DROP_BLOCK,
                missingReferenceStrategy = MissingReferenceStrategy.REMOVE_REFERENCE
            )
        )

        val rootId = UUID.randomUUID()
        val missingId = UUID.randomUUID()

        val json = """
            [
              {
                "node": {
                  "type": "test:stmt",
                  "uuid": "$rootId"
                },
                "next": "$missingId",
                "isCollapsed": false
              }
            ]
        """.trimIndent()

        val report = format.loadBlocksWithRecovery(ByteArrayInputStream(json.toByteArray()))
        val root = report.blocks.single() as StatementBlock

        assertNull(root.next)
        assertTrue(report.hasIssues)
        assertTrue(report.issues.any {
            it.kind == ScriptLoadIssue.Kind.MISSING_NEXT_BLOCK &&
                it.action == RecoveryAction.REMOVED_REFERENCE &&
                it.ownerBlockId == rootId &&
                it.targetBlockId == missingId
        })
    }

    @Test
    fun `missing output reference is replaced with expression stub`() {
        val format = createFormat()
        val rootId = UUID.randomUUID()
        val missingOutputId = UUID.randomUUID()

        val json = """
            [
              {
                "node": {
                  "type": "test:stmt",
                  "uuid": "$rootId"
                },
                "outputs": {
                  "RESULT": "$missingOutputId"
                },
                "isCollapsed": false
              }
            ]
        """.trimIndent()

        val report = format.loadBlocksWithRecovery(ByteArrayInputStream(json.toByteArray()))
        val root = report.blocks.single() as StatementBlock
        val stub = root.outputs["RESULT"]

        assertIs<BrokenExpressionBlock>(stub)
        assertEquals(missingOutputId, stub.uuid)
        assertEquals(root, stub.parentBlock)
        assertEquals("RESULT", stub.parentOutputName)
        assertTrue(report.hasIssues)
        assertTrue(report.issues.any {
            it.kind == ScriptLoadIssue.Kind.MISSING_INPUT_BLOCK &&
                it.action == RecoveryAction.REPLACED_WITH_STUB &&
                it.ownerBlockId == rootId &&
                it.targetBlockId == missingOutputId
        })
    }

    @Test
    fun `decode failure replace_with_stub keeps block in result`() {
        val format = createFormat(
            ScriptRecoveryPolicy(
                decodeFailureStrategy = DecodeFailureStrategy.REPLACE_WITH_STUB,
                missingReferenceStrategy = MissingReferenceStrategy.REMOVE_REFERENCE
            )
        )

        val brokenId = UUID.randomUUID()
        val json = """
            [
              {
                "node": {
                  "type": "test:unknown",
                  "uuid": "$brokenId"
                },
                "isCollapsed": false
              }
            ]
        """.trimIndent()

        val report = format.loadBlocksWithRecovery(ByteArrayInputStream(json.toByteArray()))

        assertEquals(1, report.blocks.size)
        assertIs<BrokenStatementBlock>(report.blocks.single())
        assertEquals(brokenId, report.blocks.single().uuid)
        assertTrue(report.issues.any {
            it.kind == ScriptLoadIssue.Kind.DECODE_FAILED &&
                it.action == RecoveryAction.REPLACED_WITH_STUB &&
                it.ownerBlockId == brokenId
        })
    }
}
