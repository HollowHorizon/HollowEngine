package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.DrawBoxCommand
import ru.hollowhorizon.hollowengine.client.ui.UiBorder
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiInsets
import ru.hollowhorizon.hollowengine.client.ui.UiMatrix4
import ru.hollowhorizon.hollowengine.client.ui.UiResolvedPaint
import ru.hollowhorizon.hollowengine.client.ui.layout.UiRect
import ru.hollowhorizon.hollowengine.client.ui.px
import ru.hollowhorizon.hollowengine.client.ui.style.UiBackfaceVisibility
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterChain
import ru.hollowhorizon.hollowengine.client.ui.style.UiImageFit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiAnalyticRectBatchTest {
    @Test
    fun `rounded fill and uniform border use one analytic quad`() {
        val batch = UiAnalyticRectBatch()
        val command = command(
            border = UiBorder(width = UiInsets.all(2.px), color = UiColor.White, radius = 12f),
        )

        assertTrue(batch.canAppend(command))
        batch.append(command, UiMatrix4.identity())

        assertEquals(6, batch.vertexCount)
        assertEquals(UiAnalyticRectBatch.RecordStride, batch.recordFloatCount)
    }

    @Test
    fun `nonuniform border remains on geometry fallback`() {
        val command = command(
            border = UiBorder(
                width = UiInsets(1.px, 2.px, 1.px, 2.px),
                color = UiColor.White,
                radius = 8f,
            ),
        )

        assertFalse(UiAnalyticRectBatch().canAppend(command))
    }

    @Test
    fun `image paint remains on textured renderer`() {
        assertFalse(UiAnalyticRectBatch().canAppend(command(paint = UiResolvedPaint.Image("test:image"))))
    }

    @Test
    fun `plain rectangle remains on cheap geometry batch`() {
        assertFalse(UiAnalyticRectBatch().canAppend(command()))
    }

    private fun command(
        paint: UiResolvedPaint = UiResolvedPaint.Color(UiColor(0.2f, 0.4f, 0.8f, 1f)),
        border: UiBorder = UiBorder(),
    ) = DrawBoxCommand(
        node = BoxNode(),
        rect = UiRect(0f, 0f, 80f, 48f),
        paint = paint,
        border = border,
        shadows = emptyList(),
        opacity = 1f,
        tint = UiColor.White,
        transform = UiMatrix4.identity(),
        renderToFramebuffer = false,
        fit = UiImageFit.STRETCH,
        slice = UiInsets.Zero,
        filter = UiFilterChain.Empty,
        backfaceVisibility = UiBackfaceVisibility.VISIBLE,
    )
}
