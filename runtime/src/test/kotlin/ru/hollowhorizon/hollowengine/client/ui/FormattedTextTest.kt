package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.style.textEffects
import ru.hollowhorizon.hollowengine.client.ui.text.Bold
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FormattedTextTest {
    @Test
    fun `formatted text reveals unicode characters without exposing markup`() {
        HollowUiSurface().use { surface ->
            surface.setContent {
                FormattedText(
                    value = "A<b>Б😀</b>C",
                    visibleCharacters = 3,
                    pendingTags = listOf("pending"),
                )
            }

            val frame = surface.frame(500f, 100f, -1f, -1f, 0L)
            val spans = frame.nodes.filterIsInstance<SpanNode>()
            assertEquals(listOf("A", "Б😀", "C"), spans.map { it.text })
            assertIs<Bold>(spans[1].resolvedSnapshot.textEffects.single())
            assertEquals(setOf("pending"), spans[2].tags)
        }
    }
}
