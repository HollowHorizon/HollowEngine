package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.DrawShapeCommand
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiCommandRenderer
import ru.hollowhorizon.hollowengine.client.ui.UiNode
import ru.hollowhorizon.hollowengine.client.ui.UiRenderCommand
import ru.hollowhorizon.hollowengine.client.ui.UiRenderPhase
import ru.hollowhorizon.hollowengine.client.ui.UiResolvedPaint
import ru.hollowhorizon.hollowengine.client.ui.px
import ru.hollowhorizon.hollowengine.client.ui.shape
import ru.hollowhorizon.hollowengine.client.ui.size
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutPipeline
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.shape.GenericShape
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import ru.hollowhorizon.hollowengine.client.ui.style.UiPaint
import kotlin.test.assertEquals
import kotlin.test.assertIs

class UiBackgroundCommandTest {
    private val shape = GenericShape { size ->
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height)
        close()
    }

    @Test
    fun `shape background combines fill and stroke in one command`() {
        val node = BoxNode(
            modifiers = listOf(
                Modifier.size(32.px, 24.px).shape(
                    shape = shape,
                    fill = UiPaint.Color(UiColor.White),
                    stroke = UiPaint.Color(UiColor.Black),
                    strokeWidth = 2.px,
                ),
            ),
        )

        val commands = render(node).filterIsInstance<DrawShapeCommand>()

        assertEquals(1, commands.size)
        val command = commands.single()
        assertIs<UiResolvedPaint.Color>(command.fill)
        assertIs<UiResolvedPaint.Color>(command.stroke)
        assertEquals(2f, command.strokeWidth)
        assertEquals(UiRenderPhase.BACKGROUND, command.phase)
    }

    private fun render(root: UiNode): List<UiRenderCommand> {
        UiModifierResolver().resolve(root)
        val layout = UiLayoutPipeline().compute(root, 1000f, 1000f, UiScrollState())
        return UiCommandRenderer().collect(root, layout)
    }
}
