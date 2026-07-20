package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.UiColor
import ru.hollowhorizon.hollowengine.client.ui.UiResolvedPaint
import ru.hollowhorizon.hollowengine.client.ui.style.UiFilterChain
import ru.hollowhorizon.hollowengine.client.ui.style.UiGradientStop
import java.nio.FloatBuffer
import kotlin.test.assertEquals

class UiPaintBufferEncoderTest {
    @Test
    fun `gradient stop keeps rgba channels separate from offset`() {
        val paints = UiFloatArrayBuilder()
        val stops = UiFloatArrayBuilder()
        val encoder = UiPaintBufferEncoder(paints, stops)
        encoder.append(
            UiResolvedPaint.LinearGradient(
                0f,
                listOf(UiGradientStop(0.25f, UiColor(0.1f, 0.2f, 0.3f, 0.4f))),
            ),
            opacity = 1f,
            filter = UiFilterChain.Empty,
            width = 100f,
            height = 50f,
        )
        val data = FloatBuffer.allocate(8)

        stops.writeTo(data)

        assertEquals(0.1f, data[0])
        assertEquals(0.2f, data[1])
        assertEquals(0.3f, data[2])
        assertEquals(0.4f, data[3])
        assertEquals(0.25f, data[4])
    }

}
