package ru.hollowhorizon.hollowengine.client.ui.layout

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutPipeline
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollOffset
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScrollAxisTest {
    private fun scrollRange(scrollModifier: Modifier): UiScrollOffset {
        val viewport = BoxNode(
            id = "viewport",
            measurePolicy = UiMeasurePolicies.box(),
            modifiers = listOf(Modifier.size(100.px, 100.px) then scrollModifier),
        )
        viewport.children.add(BoxNode(modifiers = listOf(Modifier.size(200.px, 300.px))))
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column)
        root.children.add(viewport)

        UiModifierResolver().resolve(root)
        val layout = UiLayoutPipeline().compute(root, 300f, 300f, UiScrollState())
        return layout[viewport].scrollRange
    }

    @Test
    fun `scroll(vertical) only produces a vertical range`() {
        val range = scrollRange(Modifier.scroll(vertical = true, horizontal = false))
        assertTrue(range.y > 0f, "vertical overflow should scroll")
        assertEquals(0f, range.x, "horizontal axis is disabled")
    }

    @Test
    fun `scroll(horizontal) only produces a horizontal range`() {
        val range = scrollRange(Modifier.scroll(vertical = false, horizontal = true))
        assertTrue(range.x > 0f, "horizontal overflow should scroll")
        assertEquals(0f, range.y, "vertical axis is disabled")
    }

    @Test
    fun `scroll on both axes produces both ranges`() {
        val range = scrollRange(Modifier.scroll(vertical = true, horizontal = true))
        assertTrue(range.x > 0f && range.y > 0f)
    }

    @Test
    fun `hss scroll both maps to both axes`() {
        val sheet = ru.hollowhorizon.hollowengine.client.ui.style.compileHss(".sc { scroll: both; }")
        val viewport = BoxNode(
            id = "viewport", tags = listOf("sc"), measurePolicy = UiMeasurePolicies.box(),
            modifiers = listOf(Modifier.size(100.px, 100.px)),
        )
        viewport.children.add(BoxNode(modifiers = listOf(Modifier.size(200.px, 300.px))))
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(viewport) }
        UiModifierResolver(stylesheet = sheet).resolve(root)
        val range = UiLayoutPipeline().compute(root, 300f, 300f, UiScrollState())[viewport].scrollRange
        assertTrue(range.x > 0f && range.y > 0f)
    }
}
