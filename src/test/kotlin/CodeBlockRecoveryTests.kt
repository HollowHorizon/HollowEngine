import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockRepository
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.BrokenStatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.serialization.CodeBlockFormat
import java.io.ByteArrayInputStream
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Serializable
@SerialName("test:stmt")
private class RecoveryTestStatementBlock : StatementBlock() {
    override val color: Color get() = Color.WHITE
    override suspend fun execute() = Unit
    override fun InputSlotScope.composeContent() = Unit
}

class CodeBlockRecoveryTests {
    private fun createFormat(): CodeBlockFormat {
        val repository = BlockRepository.create("Test") {
            category("Test", null) {
                block("Stmt") { RecoveryTestStatementBlock() }
            }
        }
        return CodeBlockFormat(repository)
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
}

