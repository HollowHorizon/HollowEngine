package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.DrawBoxCommand
import ru.hollowhorizon.hollowengine.client.ui.DrawShadowCommand
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
import ru.hollowhorizon.hollowengine.client.ui.style.UiShadow
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
        batch.append(command, UiMatrix4.identity(), UiShaderClip.None)

        assertEquals(1, batch.instanceCount)
        assertEquals(UiAnalyticRectBatch.RecordStride, batch.recordFloatCount)
    }

    @Test
    fun `each record carries its clip rectangle`() {
        val batch = UiAnalyticRectBatch()
        val command = command(
            border = UiBorder(width = UiInsets.all(2.px), color = UiColor.White, radius = 12f),
        )
        val clip = UiShaderClip(4f, 8f, 40f, 30f)
        batch.append(command, UiMatrix4.identity(), clip)

        // The clip rect is the last texel (floats 12..15) of the 16-float record.
        val records = FloatArray(batch.recordFloatCount)
        val buffer = java.nio.FloatBuffer.wrap(records)
        batch.writeRecords(buffer)
        assertEquals(clip.minX, records[12])
        assertEquals(clip.minY, records[13])
        assertEquals(clip.maxX, records[14])
        assertEquals(clip.maxY, records[15])
    }

    @Test
    fun `an instance encodes the row-major transform then the local quad bounds`() {
        val batch = UiAnalyticRectBatch()
        batch.append(
            command(border = UiBorder(width = UiInsets.all(2.px), color = UiColor.White, radius = 12f)),
            UiMatrix4.identity(),
            UiShaderClip.None,
        )

        val instance = FloatArray(batch.instanceFloatCount)
        batch.writeInstances(java.nio.FloatBuffer.wrap(instance))
        assertEquals(UiAnalyticRectBatch.InstanceStride, instance.size)
        // Rows 0-3 of the identity matrix.
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        identity.forEachIndexed { index, value -> assertEquals(value, instance[index], "row float $index") }
        // Local bounds (0,0)-(w,h).
        assertEquals(0f, instance[16]); assertEquals(0f, instance[17])
        assertEquals(80f, instance[18]); assertEquals(48f, instance[19])
    }

    @Test
    fun `a glyph encodes marker, uv, colour and clip into its record and tracks the atlas`() {
        val batch = UiAnalyticRectBatch()
        val clip = UiShaderClip(1f, 2f, 3f, 4f)
        batch.appendGlyph(
            UiMatrix4.identity(),
            10f, 20f, 30f, 40f,          // local bounds
            0.1f, 0.2f, 0.3f, 0.4f,       // uv rect
            UiColor(0.5f, 0.6f, 0.7f, 0.8f),
            clip,
            atlas = 42, distanceRange = 2f, atlasWidth = 512f, atlasHeight = 8192f,
        )

        assertEquals(1, batch.instanceCount)
        assertEquals(42, batch.atlasTextureId)
        assertTrue(batch.acceptsGlyphAtlas(42))
        assertFalse(batch.acceptsGlyphAtlas(7))

        val records = FloatArray(batch.recordFloatCount)
        batch.writeRecords(java.nio.FloatBuffer.wrap(records))
        assertEquals(UiAnalyticRectBatch.GlyphMarker, records[0], "glyph marker in texel 0")
        assertEquals(0.1f, records[4]); assertEquals(0.4f, records[7]) // uv rect (texel 1)
        assertEquals(0.5f, records[8]); assertEquals(0.8f, records[11]) // colour (texel 2)
        assertEquals(1f, records[12]); assertEquals(4f, records[15]) // clip (texel 3)
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
    fun `plain fill joins the analytic batch and is hard-filled by the shader`() {
        // A flat colour rect (no radius, no border) batches here; the shader hard-fills it so abutting
        // opaque fills stay seam-free while still sharing one instanced draw with rounded/bordered rects.
        val batch = UiAnalyticRectBatch()
        val command = command()

        assertTrue(batch.canAppend(command))
        batch.append(command, UiMatrix4.identity(), UiShaderClip.None)

        assertEquals(1, batch.instanceCount)
        val records = FloatArray(batch.recordFloatCount)
        batch.writeRecords(java.nio.FloatBuffer.wrap(records))
        assertEquals(0f, records[2], "radius")
        assertEquals(0f, records[3], "border width")
    }

    @Test
    fun `image fill stays off the SDF pipeline`() {
        assertFalse(UiAnalyticRectBatch().canAppend(command(paint = UiResolvedPaint.Image("test:image"))))
    }

    @Test
    fun `rounded shadow uses one expanded analytic quad`() {
        val node = BoxNode()
        val command = DrawShadowCommand(
            node = node,
            rect = UiRect(0f, 0f, 80f, 48f),
            radius = 12f,
            shape = null,
            shadows = emptyList(),
            opacity = 0.8f,
            transform = UiMatrix4.identity(),
            filter = UiFilterChain.Empty,
            backfaceVisibility = UiBackfaceVisibility.VISIBLE,
        )

        val batch = UiAnalyticRectBatch()
        batch.appendShadow(
            command, UiShadow(blur = 6f, spread = 2f, color = UiColor.Black), command.transform, UiShaderClip.None,
        )

        assertEquals(1, batch.instanceCount)
        assertEquals(UiAnalyticRectBatch.RecordStride, batch.recordFloatCount)
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
