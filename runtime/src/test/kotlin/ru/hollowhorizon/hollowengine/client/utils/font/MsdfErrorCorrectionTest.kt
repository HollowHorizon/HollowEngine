package ru.hollowhorizon.hollowengine.client.utils.font

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MsdfErrorCorrectionTest {
    private val size = 32
    private val range = 8f

    @Test
    fun `a clean field is left untouched`() {
        val field = squareField()
        val before = field.copyOf()
        correct(field)
        assertContentEquals(before, field, "a square generates no artifacts and must survive intact")
    }

    @Test
    fun `an injected artifact is flattened to its own median`() {
        val field = squareField()
        injectArtifact(field)
        correct(field)

        for (texel in listOf(index(14, 16), index(15, 16))) {
            assertEquals(0.6f, field[texel], 1e-6f, "red flattened to the median")
            assertEquals(0.6f, field[texel + 1], 1e-6f, "green flattened to the median")
            assertEquals(0.6f, field[texel + 2], 1e-6f, "blue flattened to the median")
        }
    }

    @Test
    fun `correcting a texel does not disturb the rest of the field`() {
        val field = squareField()
        injectArtifact(field)
        val injected = field.copyOf()
        correct(field)

        val corrupted = setOf(index(14, 16), index(15, 16))
        for (y in 0 until size) {
            for (x in 0 until size) {
                val texel = index(x, y)
                if (texel in corrupted) continue
                for (channel in 0..2) {
                    assertEquals(injected[texel + channel], field[texel + channel], "texel ($x, $y) channel $channel")
                }
            }
        }
    }

    private fun injectArtifact(field: FloatArray) {
        val left = index(14, 16)
        val right = index(15, 16)
        field[left] = 0f; field[left + 1] = 1f; field[left + 2] = 0.6f
        field[right] = 1f; field[right + 1] = 0f; field[right + 2] = 0.6f
    }

    @Test
    fun `the channel disagreement at a corner survives`() {
        val field = squareField()
        val spreadBefore = spreadAt(field, 5, 7)
        assertTrue(spreadBefore > 0.2f, "the fixture has a corner to protect: $spreadBefore")
        correct(field)
        assertEquals(spreadBefore, spreadAt(field, 5, 7), 1e-6f, "the corner's channels were flattened")
    }

    @Test
    fun `a field of a single value has nothing to correct`() {
        val field = FloatArray(size * size * 3) { 1f }
        val before = field.copyOf()
        correct(field)
        assertContentEquals(before, field)
    }

    private fun correct(field: FloatArray) = correctMsdfErrors(
        field = field,
        width = size,
        height = size,
        shape = squareShape(left = 8f, bottom = 8f, size = 16f),
        scale = 1f,
        translateX = 0f,
        translateY = 0f,
        pixelRange = range,
    )

    private fun squareField(): FloatArray {
        val output = FloatArray(size * size * 3)
        generateMsdf(squareShape(left = 8f, bottom = 8f, size = 16f), size, size, 1f, 0f, 0f, range, output)
        return output
    }

    private fun spreadAt(field: FloatArray, x: Int, y: Int): Float = field.channelSpreadAt(x, y, size)

    private fun index(x: Int, y: Int) = (y * size + x) * 3
}
