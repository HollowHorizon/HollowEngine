package ru.hollowhorizon.hollowengine.client.gui.scripting

import ru.hollowhorizon.hollowengine.client.ui.ide.preview.HollowIdeTextModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HollowIdeTextModelTest {
    private class Parsed(val text: String)

    @Test
    fun `the preview's own write keeps the model it edited`() {
        var parses = 0
        val model = HollowIdeTextModel { text -> parses++; Parsed(text) }
        val first = model.read("a").getOrThrow()

        model.wrote("b")

        assertSame(first, model.read("b").getOrThrow())
        assertEquals(1, parses)
    }

    @Test
    fun `a change from the text side builds a new model`() {
        val model = HollowIdeTextModel(::Parsed)
        val first = model.read("a").getOrThrow()

        val second = model.read("b").getOrThrow()

        assertTrue(first !== second)
        assertEquals("b", second.text)
    }

    @Test
    fun `a write after a failed read does not hide the next text`() {
        val model = HollowIdeTextModel { text -> require(text != "broken"); Parsed(text) }
        assertTrue(model.read("broken").isFailure)

        model.wrote("fixed")

        assertEquals("fixed", model.read("fixed").getOrThrow().text)
    }
}
