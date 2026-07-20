package ru.hollowhorizon.hollowengine.client.ui.layout

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Default MonoCraft MSDF font: 6.667px/glyph @ fontSize 10, 6.667px space, line height = fontSize 10. */
class NestedInlineFlowTest {
    private fun span(text: String, vararg mods: Modifier) = SpanNode(text, modifiers = mods.toList())

    private fun inlineGroup(vararg mods: Modifier) = BoxNode(
        measurePolicy = UiMeasurePolicies.InlineFlow,
        modifiers = mods.toList(),
    )

    private fun flow(width: Float, build: BoxNode.() -> Unit): Pair<UiLayoutResult, BoxNode> {
        val container = BoxNode(
            id = "flow",
            measurePolicy = UiMeasurePolicies.InlineFlow,
            modifiers = listOf(Modifier.size(width.px, UiLength.Auto)),
        ).apply(build)
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(container) }
        UiModifierResolver().resolve(root)
        return UiLayoutPipeline().compute(root, 1000f, 1000f, UiScrollState()) to container
    }

    @Test
    fun `nested inline group flattens its words into the parent line`() {
        val group = inlineGroup().apply {
            children.add(span("bb cc"))
        }
        val (layout, container) = flow(500f) {
            children.add(span("aa"))
            children.add(group)
            children.add(span("dd"))
        }
        assertEquals(10f, layout.nodes.getValue(container).rect.height, 0.6f)
        val groupRect = layout.nodes.getValue(group).rect
        assertEquals(33.333f, groupRect.width, 1.5f)
        assertEquals(10f, groupRect.height, 0.6f)
    }

    @Test
    fun `nested group wraps across lines and its children stay under it in the tree`() {
        val inner = span("bb cc dd ee")
        val group = inlineGroup().apply { children.add(inner) }
        val (layout, _) = flow(60f) {
            children.add(span("aa"))
            children.add(group)
        }
        val groupRect = layout.nodes.getValue(group).rect
        val innerRect = layout.nodes.getValue(inner).rect
        assertTrue(groupRect.height > 10f, "group wrapped to multiple lines")
        assertTrue(innerRect.height > 10f, "inner span wrapped with it")
        assertTrue(innerRect.x >= groupRect.x - 0.5f && innerRect.y >= groupRect.y - 0.5f, "inner sits inside the group")
    }

    private fun groupBoxes(layout: UiLayoutResult, group: BoxNode) =
        UiCommandRenderer().collect(layout.root, layout)
            .filterIsInstance<DrawBoxCommand>()
            .filter { it.node === group && it.phase == UiRenderPhase.BACKGROUND }

    @Test
    fun `clone draws a full-radius box per line, slice rounds only the ends`() {
        val innerClone = span("bb cc dd ee")
        val cloneGroup = inlineGroup(
            Modifier.background(UiColor(0f, 1f, 0f, 0.5f)).borderRadius(4f).boxDecorationBreak(UiBoxDecorationBreak.CLONE),
        ).apply { children.add(innerClone) }
        val (cloneLayout, _) = flow(60f) { children.add(cloneGroup) }
        val cloneBoxes = groupBoxes(cloneLayout, cloneGroup)
        assertTrue(cloneBoxes.size >= 2, "one box per wrapped line")
        assertTrue(cloneBoxes.all { it.border.radius == 4f }, "clone: every line keeps the radius")

        val innerSlice = span("bb cc dd ee")
        val sliceGroup = inlineGroup(
            Modifier.background(UiColor(0f, 1f, 0f, 0.5f)).borderRadius(4f).boxDecorationBreak(UiBoxDecorationBreak.SLICE),
        ).apply { children.add(innerSlice) }
        val (sliceLayout, _) = flow(60f) { children.add(sliceGroup) }
        val sliceBoxes = groupBoxes(sliceLayout, sliceGroup)
        assertTrue(sliceBoxes.size >= 2, "one box per wrapped line")
        assertEquals(4f, sliceBoxes.first().border.radius, 0.01f, "slice: first line rounded")
        assertEquals(4f, sliceBoxes.last().border.radius, 0.01f, "slice: last line rounded")
        assertTrue(sliceBoxes.drop(1).dropLast(1).all { it.border.radius == 0f }, "slice: middle lines squared")
    }

    @Test
    fun `group decoration has no degenerate box from a bare padding piece`() {
        val inner = span("cloned group text that wraps onto more than one line for sure now")
        val group = inlineGroup(Modifier.background(UiColor(0f, 1f, 0f, 0.5f)).padding(4.px, 1.px))
            .apply { children.add(inner) }
        val (layout, _) = flow(120f) {
            children.add(span("Mixed lead words fill the very first line here"))
            children.add(group)
        }
        val g = layout.nodes.getValue(group)
        val lines = g.inlineDecoration!!.lines
        assertTrue(lines.isNotEmpty())
        assertTrue(lines.all { it.height > 0f && it.width > 0f }, "every line box wraps real content")
        val firstBoxWorldLeft = g.rect.x + lines.first().x
        assertTrue(firstBoxWorldLeft > 40f, "first line box starts after the leading text (left=$firstBoxWorldLeft)")
    }

    @Test
    fun `nested group horizontal padding advances following content`() {
        val padded = inlineGroup(Modifier.padding(20.px, 0.px)).apply { children.add(span("bb")) }
        val cc = span("cc")
        val (layout, _) = flow(500f) {
            children.add(span("aa"))
            children.add(padded)
            children.add(cc)
        }
        val ccRect = layout.nodes.getValue(cc).rect
        assertTrue(ccRect.x >= 60f, "left+right padding pushed following content (cc.x=${ccRect.x})")
    }
}
