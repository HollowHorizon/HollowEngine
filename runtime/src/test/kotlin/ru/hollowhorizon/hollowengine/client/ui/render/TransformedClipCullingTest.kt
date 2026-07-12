package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransformedClipCullingTest {
    private fun textCommands(translateX: Float?): List<DrawTextCommand> {
        val text = SpanNode("visible after transform")
        val rowModifier = Modifier.position(150.px, 0.px).let { modifier ->
            if (translateX == null) modifier else modifier.translate(x = translateX)
        }
        val row = BoxNode(
            measurePolicy = UiMeasurePolicies.Row,
            modifiers = listOf(rowModifier),
        ).also { it.children.add(text) }
        val root = BoxNode(
            modifiers = listOf(Modifier.size(100.px, 20.px).clip()),
        ).also { it.children.add(row) }

        val frame = HollowUiRuntime().frame(root, 100f, 20f, -1f, -1f, 0L)
        return UiCommandRenderer().collect(frame.root, frame.layout).filterIsInstance<DrawTextCommand>()
    }

    @Test
    fun `child moved into clip by ancestor transform is rendered`() {
        assertTrue(textCommands(translateX = -100f).isNotEmpty())
    }

    @Test
    fun `untransformed child outside clip remains culled`() {
        assertFalse(textCommands(translateX = null).isNotEmpty())
    }
}
