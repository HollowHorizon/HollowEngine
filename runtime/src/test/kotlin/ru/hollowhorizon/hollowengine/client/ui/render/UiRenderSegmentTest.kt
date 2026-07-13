package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.DrawShadowCommand
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiVec3
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.shape.GenericShape
import ru.hollowhorizon.hollowengine.client.ui.style.UiBackfaceVisibility
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterChain
import ru.hollowhorizon.hollowengine.client.ui.style.UiShadow
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiRenderSegmentTest {
    private val shape = GenericShape { size ->
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height)
        close()
    }

    @Test
    fun `shape shadow stays in the geometry segment`() {
        assertTrue(shadowCommand(hasShape = true).isSegmentBatchable())
    }

    @Test
    fun `projected rectangle shadow remains an immediate command`() {
        assertFalse(shadowCommand(hasShape = false).isSegmentBatchable())
    }

    private fun shadowCommand(hasShape: Boolean) = DrawShadowCommand(
        node = BoxNode(),
        rect = UiRect(0f, 0f, 16f, 16f),
        radius = 0f,
        shape = shape.takeIf { hasShape },
        shadows = listOf(UiShadow(offset = UiVec3(0f, 0f, 0f), blur = 2f, color = UiColor.White)),
        opacity = 1f,
        transform = UiMatrix4.identity(),
        filter = UiFilterChain.Empty,
        backfaceVisibility = UiBackfaceVisibility.VISIBLE,
    )
}
