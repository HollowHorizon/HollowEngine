package ru.hollowhorizon.hollowengine.client.ui.layout

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import kotlin.test.assertEquals

class CompositeLengthTest {
    private fun width(length: UiLength): Float {
        val child = BoxNode(id = "child", modifiers = listOf(Modifier.size(length, 100.percent)))
        val parent = BoxNode(
            id = "parent",
            measurePolicy = UiMeasurePolicies.box(UiBoxMode.FREE),
            modifiers = listOf(Modifier.size(200.px, 100.px)),
        ).also { it.children.add(child) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(parent) }
        UiModifierResolver().resolve(root)
        val layout = UiLayoutPipeline().compute(root, 400f, 400f, UiScrollState())
        return layout.nodes.getValue(child).rect.width
    }

    @Test
    fun `a length minus a length is the whole expression, not either half`() {
        assertEquals(200f, width(100.percent))
        assertEquals(160f, width(100.percent - 40.px), "the box is the parent less the gutter")
        assertEquals(200f, width(100.percent - 0.px), "taking nothing away changes nothing")
        assertEquals(140f, width(100.percent - 30.percent))
    }

    @Test
    fun `a sum adds up`() {
        assertEquals(140f, width(50.percent + 40.px))
    }
}
