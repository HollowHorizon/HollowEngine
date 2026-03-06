import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.EventOutputVariableBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.validation.CodeBlockValidator
import ru.hollowhorizon.hollowengine.common.codeblocks.validation.EventContextProvider
import ru.hollowhorizon.hollowengine.common.codeblocks.validation.EventContextValidationRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable
@SerialName("test:event")
private class TestEventStartBlock : StartBlock(), EventContextProvider {
    override val color: Color get() = Color.WHITE
    override suspend fun trigger() = Unit
    override fun InputSlotScope.composeContent() = Unit
    override fun availableEventOutputs(): Set<String> = setOf("payload")
}

@Serializable
@SerialName("test:plain")
private class PlainStartBlock : StartBlock() {
    override val color: Color get() = Color.WHITE
    override suspend fun trigger() = Unit
    override fun InputSlotScope.composeContent() = Unit
}

class CodeBlockValidationTests {
    private val validator = CodeBlockValidator(listOf(EventContextValidationRule))

    @Test
    fun `event output variable is valid inside matching event branch`() {
        val start = TestEventStartBlock()
        val expression = EventOutputVariableBlock("payload")
        start.attachInput("value", expression)

        val issues = validator.validate(listOf(start))

        assertTrue(issues.isEmpty())
    }

    @Test
    fun `event output variable outside event branch produces validation issue`() {
        val start = PlainStartBlock()
        val expression = EventOutputVariableBlock("payload")
        start.attachInput("value", expression)

        val issues = validator.validate(listOf(start))

        assertEquals(1, issues.size)
        assertTrue(issues.single().message.contains("event block", ignoreCase = true))
    }
}
