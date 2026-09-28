package ru.hollowhorizon.hollowengine.client.ui.layout

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiMeasurePolicies
import ru.hollowhorizon.hollowengine.client.ui.border
import ru.hollowhorizon.hollowengine.client.ui.clip
import ru.hollowhorizon.hollowengine.client.ui.padding
import ru.hollowhorizon.hollowengine.client.ui.px
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.scrollModifier
import ru.hollowhorizon.hollowengine.client.ui.size
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import kotlin.test.assertEquals

class OverflowClipTest {
    private fun clipOf(modifier: Modifier): UiRect {
        val node = BoxNode(id = "node", measurePolicy = UiMeasurePolicies.box(), modifiers = listOf(modifier))
        val root = BoxNode(id = "root", measurePolicy = UiMeasurePolicies.box(), modifiers = emptyList())
        root.children.add(node)
        UiModifierResolver().resolve(root)
        return UiLayoutPipeline().compute(root, 300f, 300f, UiScrollState()).nodes.getValue(node).clip!!
    }

    private val field = Modifier.size(100.px, 16.px).padding(4.px).border(1.px, UiColor.White)

    @Test
    fun `a single-line field keeps the padding band along the axis it does not scroll`() {
        val clip = clipOf(field.then(scrollModifier(vertical = false)))

        assertEquals(5f, clip.x)
        assertEquals(90f, clip.width)
        assertEquals(1f, clip.y)
        assertEquals(14f, clip.height)
    }

    @Test
    fun `a container scrolling both ways and a plain clip both cut at the content edge`() {
        val scrolling = clipOf(field.then(scrollModifier()))
        val clipped = clipOf(field.clip())

        for (clip in listOf(scrolling, clipped)) {
            assertEquals(UiRect(5f, 5f, 90f, 6f), clip)
        }
    }
}
