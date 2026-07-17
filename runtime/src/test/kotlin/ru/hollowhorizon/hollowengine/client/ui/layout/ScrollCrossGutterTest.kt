package ru.hollowhorizon.hollowengine.client.ui.layout

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import kotlin.test.assertEquals

/**
 * A fill/percent scroll container can't grow to fit its scrollbars, so they sit INSIDE the content and
 * the horizontal scroll range must clear the vertical scrollbar's gutter (and vice versa). Default
 * gutter = thickness(3.5) + margin(3)*2 = 9.5px. These lock down the model editor's node tree, whose
 * rows are auto-width (fillRowWidth = false) flow rows with wrap:false labels.
 */
class ScrollCrossGutterTest {
    private val gutter = 9.5f
    private val parentWidth = 200f
    private val rowWidth = 600f

    private fun scroller(policy: UiMeasurePolicy, rowFactory: () -> BoxNode, rows: Int): Pair<UiLayoutResult, UiNode> {
        val node = BoxNode(
            id = "scroller",
            measurePolicy = policy,
            modifiers = listOf(Modifier.size(100.percent, 100.percent).scroll(vertical = true, horizontal = true)),
        )
        repeat(rows) { node.children.add(rowFactory()) }
        val parent = BoxNode(
            measurePolicy = UiMeasurePolicies.Column,
            modifiers = listOf(Modifier.size(parentWidth.px, 100f.px)),
        ).also { it.children.add(node) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(parent) }
        UiModifierResolver().resolve(root)
        return UiLayoutPipeline().compute(root, 400f, 400f, UiScrollState()) to node
    }

    private fun rangeX(policy: UiMeasurePolicy, rowFactory: () -> BoxNode, rows: Int): Float {
        val (layout, node) = scroller(policy, rowFactory, rows)
        return layout.nodes.getValue(node).scrollRange.x
    }

    private fun fixedRow(height: Float) = BoxNode(modifiers = listOf(Modifier.size(rowWidth.px, height.px)))

    private fun autoRow(height: Float) = BoxNode(
        measurePolicy = UiMeasurePolicies.Row,
        modifiers = listOf(Modifier.size(UiLength.Auto, height.px)),
    ).also { it.children.add(BoxNode(modifiers = listOf(Modifier.size(rowWidth.px, height.px)))) }

    /** Rightmost content must be scrollable to the left of the vertical scrollbar. */
    private val expectedRangeX get() = rowWidth - (parentWidth - gutter)

    @Test
    fun `fixed rows - both overflow`() =
        assertEquals(expectedRangeX, rangeX(UiMeasurePolicies.Column, { fixedRow(40f) }, 10), 1f)

    @Test
    fun `auto rows - column`() =
        assertEquals(expectedRangeX, rangeX(UiMeasurePolicies.Column, { autoRow(40f) }, 10), 1f)

    @Test
    fun `auto rows - lazy column`() =
        assertEquals(expectedRangeX, rangeX(UiMeasurePolicies.Column, { autoRow(40f) }, 10), 1f)

    @Test
    fun `auto rows - mutual trigger (horizontal bar reveals a vertical one)`() =
        assertEquals(expectedRangeX, rangeX(UiMeasurePolicies.Column, { autoRow(48f) }, 2), 1f)

    @Test
    fun `wrap-false text labels keep their natural width`() {
        fun label() = BoxNode(
            measurePolicy = UiMeasurePolicies.InlineFlow,
            modifiers = listOf(Modifier.size(UiLength.Auto, 24.px).textWrap(false)),
        ).also { it.children.add(SpanNode("bone37_very_long_overflowing_name")) }

        val probe = label()
        val probeRoot = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(probe) }
        UiModifierResolver().resolve(probeRoot)
        val natural = UiLayoutPipeline().compute(probeRoot, 4000f, 400f, UiScrollState()).nodes.getValue(probe).rect.width

        val node = BoxNode(
            id = "s",
            measurePolicy = UiMeasurePolicies.Column,
            modifiers = listOf(Modifier.size(100.percent, 100.percent).scroll(vertical = true, horizontal = true)),
        )
        repeat(10) { node.children.add(label()) }
        val parent = BoxNode(measurePolicy = UiMeasurePolicies.Column, modifiers = listOf(Modifier.size(120.px, 60.px))).also { it.children.add(node) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(parent) }
        UiModifierResolver().resolve(root)
        val ln = UiLayoutPipeline().compute(root, 400f, 400f, UiScrollState()).nodes.getValue(node)
        assertEquals(natural - (120f - gutter), ln.scrollRange.x, 1f)
    }

    @Test
    fun `lazy column counts the widest row even when off-screen`() {
        val node = BoxNode(
            id = "s",
            measurePolicy = UiMeasurePolicies.Column,
            modifiers = listOf(Modifier.size(100.percent, 100.percent).scroll(vertical = true, horizontal = true)),
        )
        repeat(20) { i -> node.children.add(BoxNode(modifiers = listOf(Modifier.size((if (i == 15) 600f else 50f).px, 24.px)))) }
        val parent = BoxNode(measurePolicy = UiMeasurePolicies.Column, modifiers = listOf(Modifier.size(120.px, 60.px))).also { it.children.add(node) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(parent) }
        UiModifierResolver().resolve(root)
        val ln = UiLayoutPipeline().compute(root, 400f, 400f, UiScrollState()).nodes.getValue(node)
        assertEquals(600f - ln.content.width, ln.scrollRange.x, 1f)
    }

    @Test
    fun `tree nested in a scrollable sidebar clears its own vertical scrollbar`() {
        val tree = BoxNode(
            id = "tree",
            measurePolicy = UiMeasurePolicies.Column,
            modifiers = listOf(Modifier.size(100.percent, 120.px).scroll(vertical = true, horizontal = true)),
        )
        repeat(20) { tree.children.add(BoxNode(modifiers = listOf(Modifier.size(600.px, 24.px)))) }
        val filler = BoxNode(modifiers = listOf(Modifier.size(100.percent, 400.px)))
        val sidebar = BoxNode(
            measurePolicy = UiMeasurePolicies.Column,
            modifiers = listOf(Modifier.size(200.px, 200.px).scroll(vertical = true)),
        ).also { it.children.add(tree); it.children.add(filler) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(sidebar) }
        UiModifierResolver().resolve(root)
        val ln = UiLayoutPipeline().compute(root, 400f, 600f, UiScrollState()).nodes.getValue(tree)
        assertEquals(600f - ln.content.width, ln.scrollRange.x, 1f)
    }
}

class ScrollRangeStabilityTest {
    @Test
    fun `horizontal range does not shrink as you scroll`() {
        val node = BoxNode(
            id = "s",
            measurePolicy = UiMeasurePolicies.Column,
            modifiers = listOf(Modifier.size(100.percent, 100.percent).scroll(vertical = true, horizontal = true)),
        )
        repeat(10) { node.children.add(BoxNode(modifiers = listOf(Modifier.size(600.px, 40.px)))) }
        val parent = BoxNode(measurePolicy = UiMeasurePolicies.Column, modifiers = listOf(Modifier.size(200.px, 100.px)))
            .also { it.children.add(node) }
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(parent) }
        UiModifierResolver().resolve(root)

        val scrollState = UiScrollState()
        val pipeline = UiLayoutPipeline()
        val range1 = pipeline.compute(root, 400f, 400f, scrollState).nodes.getValue(node).scrollRange.x

        scrollState.setImmediate(node, x = range1) // scroll all the way right
        val range2 = pipeline.compute(root, 400f, 400f, scrollState).nodes.getValue(node).scrollRange.x

        // Regression: the range must NOT depend on the current scroll offset. It used to collapse
        // (409.5 -> 0 at max scroll) because virtualContentBounds left the cross axis scroll-shifted.
        assertEquals(range1, range2, 1f, "range must be scroll-independent; range1=$range1 range2=$range2")
    }
}
