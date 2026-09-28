package ru.hollowhorizon.hollowengine.client.ui.render

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.UiBorderDash
import kotlin.math.PI
import kotlin.test.assertEquals

class BorderDashPatternTest {
    @Test
    fun `the pattern closes round the ring on a whole number of repeats`() {
        val pattern = borderDashPattern(width = 101f, height = 47f, radius = 8f, borderWidth = 1f, dash = UiBorderDash(5f, 4f))
        val perimeter = 2f * (100f - 15f) + 2f * (46f - 15f) + 2f * PI.toFloat() * 7.5f
        val repeats = perimeter / pattern.period

        assertEquals(Math.round(repeats).toFloat(), repeats, 0.001f)
        assertEquals(5f / 9f, pattern.length / pattern.period, 0.0001f, "stretching keeps the dash to gap ratio")
    }
}
