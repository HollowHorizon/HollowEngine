package ru.hollowhorizon.hollowengine.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ModifierImmutabilityTest {
    @Test
    fun `reusing a modifier does not share appended event handlers`() {
        val base = Modifier.size(100.px, 24.px).grow(1f)
        val firstHandler: (UiEvent) -> Unit = {}
        val secondHandler: (UiEvent) -> Unit = {}

        val first = base.onClick(firstHandler)
        val second = base.onClick(secondHandler)

        assertEquals(0, base.eventHandlers().size)
        assertEquals(1, first.eventHandlers().size)
        assertEquals(1, second.eventHandlers().size)
        assertSame(firstHandler, first.eventHandlers().single())
        assertSame(secondHandler, second.eventHandlers().single())
    }
}

private fun Modifier.eventHandlers(): List<(UiEvent) -> Unit> =
    (this as CompositeModifier).flatten()
        .filterIsInstance<EventModifier>()
        .map(EventModifier::handler)
