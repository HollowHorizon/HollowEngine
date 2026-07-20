package ru.hollowhorizon.hollowengine.client.ui.widgets

import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.UiEvent
import ru.hollowhorizon.hollowengine.client.ui.UiEventKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextFieldCompletionStateTest {
    private fun editor(text: String, caret: Int = text.length): Pair<TextFieldState, TextFieldCompletionState> {
        val state = TextFieldState(initialText = text, initialCaret = caret, multiline = true, autoPairs = false)
        val completion = TextFieldCompletionState(state)
        return state to completion
    }

    private fun contributorOf(vararg labels: String) = UiCompletionContributor { _ ->
        labels.map { UiTextCompletion(label = it) }
    }

    private fun TextFieldState.press(key: Int, completion: TextFieldCompletionState): Boolean {
        val input = UiKeyInput(UiEvent(UiEventKind.KEY_PRESSED, node = BoxNode(), key = key, modifiers = 0))
        return handleEditableFieldKey(this, input, layout = null, completion = completion)
    }

    @Test
    fun `typing a trigger character opens the popup`() {
        val (state, completion) = editor("val x = fo")
        completion.contributor = contributorOf("foo", "forEach")
        state.typeCharacter('o')
        completion.onCharTyped('o')
        assertTrue(completion.active)
        assertEquals(2, completion.items.size)
    }

    @Test
    fun `accept replaces the word around the caret and closes the popup`() {
        val (state, completion) = editor("val x = fo")
        completion.contributor = contributorOf("forEach")
        completion.open()
        assertTrue(completion.active)
        assertTrue(completion.accept())
        assertEquals("val x = forEach", state.text)
        assertEquals("val x = forEach".length, state.caret)
        assertFalse(completion.active)
    }

    @Test
    fun `accept honours caretOffset and importFqName`() {
        val (state, completion) = editor("fun main() {\n    Vec\n}", caret = 20)
        completion.contributor = UiCompletionContributor {
            listOf(UiTextCompletion(label = "Vec3", insertText = "Vec3()", caretOffset = 5, importFqName = "org.example.Vec3"))
        }
        completion.open()
        assertTrue(completion.accept())
        assertTrue(state.text.contains("import org.example.Vec3"))
        assertTrue(state.text.contains("Vec3()"))
        assertEquals(')', state.text[state.caret])
    }

    @Test
    fun `import stays after file annotation without package directive`() {
        val source = "@file:Suppress(\"unused\")\n\nfun main() { Vec }"
        val (state, completion) = editor(source, caret = source.indexOf("Vec") + 3)
        completion.contributor = UiCompletionContributor {
            listOf(UiTextCompletion(label = "Vec3", importFqName = "org.example.Vec3"))
        }

        completion.open()
        assertTrue(completion.accept())

        assertTrue(state.text.startsWith("@file:Suppress(\"unused\")\n\nimport org.example.Vec3"))
        assertTrue(state.text.contains("fun main() { Vec3 }"))
    }

    @Test
    fun `import stays after multiline file annotation and package directive`() {
        val source = """@file:Suppress(
            |    "unused",
            |)
            |
            |package sample
            |
            |fun main() { Vec }
        """.trimMargin()
        val (state, completion) = editor(source, caret = source.indexOf("Vec") + 3)
        completion.contributor = UiCompletionContributor {
            listOf(UiTextCompletion(label = "Vec3", importFqName = "org.example.Vec3"))
        }

        completion.open()
        assertTrue(completion.accept())

        assertTrue(state.text.indexOf("@file:Suppress") < state.text.indexOf("package sample"))
        assertTrue(state.text.indexOf("package sample") < state.text.indexOf("import org.example.Vec3"))
        assertTrue(state.text.contains("fun main() { Vec3 }"))
    }

    @Test
    fun `escape closes and arrows navigate with wrap-around`() {
        val (state, completion) = editor("ab")
        completion.contributor = contributorOf("abc", "abd", "abe")
        completion.open()

        assertTrue(state.press(GLFW.GLFW_KEY_DOWN, completion))
        assertEquals(1, completion.selectedIndex)
        assertTrue(state.press(GLFW.GLFW_KEY_UP, completion))
        assertTrue(state.press(GLFW.GLFW_KEY_UP, completion))
        assertEquals(2, completion.selectedIndex, "wraps to the last item")

        assertTrue(state.press(GLFW.GLFW_KEY_ESCAPE, completion))
        assertFalse(completion.active)
    }

    @Test
    fun `caret leaving the invocation line closes the popup on the next frame`() {
        val (state, completion) = editor("one\ntwo")
        completion.contributor = contributorOf("twofold")
        completion.open()
        assertTrue(completion.active)

        completion.onFrame()
        assertTrue(completion.active, "stays open while the caret is on the line")

        state.moveCaret(0)
        completion.onFrame()
        assertFalse(completion.active)
    }

    @Test
    fun `non-typing edits close the popup on the next frame`() {
        val (state, completion) = editor("word")
        completion.contributor = contributorOf("wordy")
        completion.open()
        completion.onFrame()
        assertTrue(completion.active)

        state.backspace()
        completion.onFrame()
        assertFalse(completion.active)
    }

    @Test
    fun `empty contributor results retry while pending and open once results appear`() {
        val (state, completion) = editor("wor")
        var results = emptyList<UiTextCompletion>()
        completion.contributor = UiCompletionContributor { results }

        state.typeCharacter('d')
        completion.onCharTyped('d')
        assertFalse(completion.active, "nothing to show yet")

        // Analysis finished: the same frame hook picks the results up.
        results = listOf(UiTextCompletion(label = "wordy"))
        completion.onFrame(revision = 1L)
        assertTrue(completion.active)
        assertEquals("wordy", completion.items.single().label)
    }

    @Test
    fun `zero-length and multi-line diagnostics are bucketed per line`() {
        val lines = editableFieldLines("abc\ndef\nghi")
        val buckets = bucketDiagnosticsByLine(
            lines,
            listOf(
                UiTextDiagnostic(start = 2, end = 6, message = "spans lines"),
                UiTextDiagnostic(start = 9, end = 9, message = "zero length"),
            ),
        )
        assertEquals(listOf(2 to 3), buckets[0].map { it.start to it.end })
        assertEquals(listOf(0 to 2), buckets[1].map { it.start to it.end })
        assertEquals(listOf(1 to 2), buckets[2].map { it.start to it.end })
    }
}
