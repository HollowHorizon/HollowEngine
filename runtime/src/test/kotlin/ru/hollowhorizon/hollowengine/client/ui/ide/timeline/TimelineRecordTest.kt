package ru.hollowhorizon.hollowengine.client.ui.ide.timeline

import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineRecordTest {
    @Test
    fun `the first key recorded away from the start keeps what the value was at zero`() {
        val timeline = TimelineController()
        val property = timeline.addProperty(listOf("node"), AnimProperty("size", "Size", FloatPropertyType(), 1f))
        timeline.applyCurrentTime(2f)

        timeline.recordKeys(property, mapOf(0 to 5f), previous = floatArrayOf(3f))

        val keys = property.curves[0].keyframes.map { it.time to it.value }
        assertEquals(listOf(0f to 3f, 2f to 5f), keys)
    }

    @Test
    fun `recording where a key already is replaces it, and the rest of the curve stays`() {
        val timeline = TimelineController()
        val property = timeline.addProperty(listOf("node"), AnimProperty("size", "Size", FloatPropertyType(), 1f))
        timeline.applyCurrentTime(2f)
        timeline.recordKeys(property, mapOf(0 to 5f), previous = floatArrayOf(3f))

        timeline.recordKeys(property, mapOf(0 to 8f), previous = floatArrayOf(0f))

        val keys = property.curves[0].keyframes.map { it.time to it.value }
        assertEquals(listOf(0f to 3f, 2f to 8f), keys)
    }
}
