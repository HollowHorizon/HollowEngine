package ru.hollowhorizon.hollowengine.client.ui.input

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.*
import ru.hollowhorizon.hollowengine.client.ui.widgets.SliderNode
import kotlin.test.assertEquals

/** The state store keys widget state by stable id, so it survives a Compose instance swap. */
class WidgetStateStoreTest {
    @Test
    fun `widget state survives a node instance swap when the id is stable`() {
        val store = UiNodeStateStore()
        val original = SliderNode(value = 0.7f, id = "volume")
        store.save(original)

        // Recomposition replaces the instance but keeps the id.
        val replacement = SliderNode(value = 0f, id = "volume")
        val root = BoxNode().also { it.children.add(replacement) }
        store.apply(root)

        assertEquals(0.7f, replacement.value, 1e-5f)
    }

    @Test
    fun `state is not applied across different ids`() {
        val store = UiNodeStateStore()
        store.save(SliderNode(value = 0.9f, id = "a"))
        val other = SliderNode(value = 0.1f, id = "b")
        store.apply(BoxNode().also { it.children.add(other) })
        assertEquals(0.1f, other.value, 1e-5f)
    }
}
