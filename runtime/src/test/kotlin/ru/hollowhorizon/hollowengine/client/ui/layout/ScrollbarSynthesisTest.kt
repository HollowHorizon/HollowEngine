package ru.hollowhorizon.hollowengine.client.ui.layout

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutPipeline
import ru.hollowhorizon.hollowengine.client.ui.layout.UiLayoutResult
import ru.hollowhorizon.hollowengine.client.ui.scroll.ScrollbarThumbNode
import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollState
import ru.hollowhorizon.hollowengine.client.ui.style.UiModifierResolver
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScrollbarSynthesisTest {
    private fun layout(childSize: UiSize, scroll: Modifier = Modifier.scroll(vertical = true)): Pair<UiLayoutResult, BoxNode> {
        val viewport = BoxNode(
            id = "viewport",
            measurePolicy = UiMeasurePolicies.box(),
            modifiers = listOf(Modifier.size(100.px, 100.px) then scroll),
        )
        viewport.children.add(BoxNode(modifiers = listOf(Modifier.size(childSize.width, childSize.height))))
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(viewport) }
        UiModifierResolver().resolve(root)
        return UiLayoutPipeline().compute(root, 300f, 300f, UiScrollState()) to viewport
    }

    @Test
    fun `a scrollbar node is synthesized and placed at the right edge on overflow`() {
        val (layout, viewport) = layout(UiSize(80.px, 300.px))
        val bars = layout.scrollbars[viewport]
        assertTrue(!bars.isNullOrEmpty(), "overflow should synthesize a scrollbar")

        val bar = assertNotNull(bars).single()
        val barLayout = assertNotNull(layout.nodes[bar], "scrollbar is placed")
        val thumbLayout = assertNotNull(layout.nodes[bar.thumb], "scrollbar thumb is placed")
        assertTrue(barLayout.rect.width < 10f, "vertical track is thin")
        assertTrue(barLayout.rect.height > 40f, "track spans most of the viewport")
        assertTrue(thumbLayout.rect.height < barLayout.rect.height, "thumb is shorter than the track")
        assertTrue(barLayout.rect.x > 80f, "track is at the right edge (x=${barLayout.rect.x})")
    }

    @Test
    fun `hit-testing the synthesized thumb in the gutter returns the thumb`() {
        val (layout, viewport) = layout(UiSize(80.px, 300.px))
        val thumb = layout.scrollbars.getValue(viewport).single().thumb
        val root = layout.root
        val frame = HollowUiFrame(root = root, nodes = listOf(root, viewport), layout = layout)
        val r = layout.nodes.getValue(thumb).rect
        val hit = frame.hitTest(r.x + r.width / 2f, r.y + r.height / 2f)
        assertTrue(hit?.node === thumb, "thumb in the gutter must be hittable (was ${hit?.node?.type})")
    }

    @Test
    fun `no scrollbar is synthesized when content fits`() {
        val (layout, viewport) = layout(UiSize(50.px, 50.px))
        assertTrue(layout.scrollbars[viewport].isNullOrEmpty(), "no overflow → no scrollbar")
    }

    @Test
    fun `reserving scrollbar adds a gutter while overlay keeps the original outer size`() {
        val (reservedLayout, reservedViewport) = layout(UiSize(80.px, 300.px))
        val (overlayLayout, overlayViewport) = layout(
            UiSize(80.px, 300.px),
            Modifier.scroll(vertical = true, overlay = true),
        )

        val reservedWidth = reservedLayout.nodes.getValue(reservedViewport).content.width
        val overlayWidth = overlayLayout.nodes.getValue(overlayViewport).content.width
        assertEquals(overlayWidth, reservedWidth, 0.01f, "the reserved gutter must not steal usable width")
        assertTrue(
            reservedLayout.nodes.getValue(reservedViewport).rect.width >
                    overlayLayout.nodes.getValue(overlayViewport).rect.width,
            "a reserving scrollbar must contribute its gutter to the outer layout size",
        )
        assertTrue(!overlayLayout.scrollbars[overlayViewport].isNullOrEmpty(), "overlay still synthesizes a scrollbar")
    }

    @Test
    fun `horizontal scrollbar reservation does not displace centered content`() {
        fun horizontalLayout(childWidth: Float): Triple<UiLayoutResult, BoxNode, BoxNode> {
            val child = BoxNode(
                id = "content",
                modifiers = listOf(Modifier.size(childWidth.px, 10.px).align(vertical = UiAlign.CENTER)),
            )
            val viewport = BoxNode(
                id = "viewport",
                measurePolicy = UiMeasurePolicies.box(),
                modifiers = listOf(Modifier.size(100.px, 22.px).scroll(vertical = false, horizontal = true)),
            ).also { it.children.add(child) }
            val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(viewport) }
            UiModifierResolver().resolve(root)
            return Triple(UiLayoutPipeline().compute(root, 300f, 300f, UiScrollState()), viewport, child)
        }

        val (fittingLayout, fittingViewport, fittingChild) = horizontalLayout(80f)
        val (overflowLayout, overflowViewport, overflowChild) = horizontalLayout(180f)
        val fittingY = fittingLayout.nodes.getValue(fittingChild).rect.y -
                fittingLayout.nodes.getValue(fittingViewport).rect.y
        val overflowY = overflowLayout.nodes.getValue(overflowChild).rect.y -
                overflowLayout.nodes.getValue(overflowViewport).rect.y
        val overflowViewportLayout = overflowLayout.nodes.getValue(overflowViewport)

        assertEquals(fittingY, overflowY, 0.01f, "adding a scrollbar must not move centred content")
        assertEquals(22f, overflowViewportLayout.content.height, 0.01f)
        assertTrue(overflowLayout.scrollbars[overflowViewport].orEmpty().isNotEmpty())
    }

    @Test
    fun `identity of a synthesized scrollbar is stable across recomputes`() {
        val viewport = BoxNode(
            id = "v", measurePolicy = UiMeasurePolicies.box(),
            modifiers = listOf(Modifier.size(100.px, 100.px).scroll(vertical = true)),
        )
        viewport.children.add(BoxNode(modifiers = listOf(Modifier.size(80.px, 300.px))))
        val root = BoxNode(measurePolicy = UiMeasurePolicies.Column).also { it.children.add(viewport) }
        val pipeline = UiLayoutPipeline()
        UiModifierResolver().resolve(root)
        val first = pipeline.compute(root, 300f, 300f, UiScrollState()).scrollbars.getValue(viewport).single()
        val second = pipeline.compute(root, 300f, 300f, UiScrollState()).scrollbars.getValue(viewport).single()
        assertTrue(first === second, "same pipeline reuses the cached scrollbar node")
    }
}
