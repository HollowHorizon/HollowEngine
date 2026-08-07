package ru.hollowhorizon.hollowengine.client.ui.ide

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.widgets.TextFieldState
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiTextCaret
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinStringPasteTransformerTest {
    @Test
    fun `regular strings escape pasted text without changing its value`() {
        val pasted = "quote=\" slash=\\ dollar=${'$'} line=\n tab=\t back=\b form=\u000C nul=\u0000"

        assertEquals(
            "quote=\\\" slash=\\\\ dollar=\\${'$'} line=\\n tab=\\t back=\\b form=\\f nul=\\u0000",
            transformAt("val text = \"be|fore\"", pasted),
        )
    }

    @Test
    fun `text outside a string is pasted unchanged`() {
        val pasted = "\"quoted\"\n${'$'}value\\path"

        assertEquals(pasted, transformAt("val value = |unit", pasted))
        assertEquals(pasted, transformAt("// \"not a |string\"", pasted))
        assertEquals(pasted, transformAt("val char = '|x'", pasted))
    }

    @Test
    fun `template expressions are code while surrounding text remains a string`() {
        val pasted = "\"\n${'$'}"

        assertEquals(pasted, transformAt("val text = \"before ${'$'}{va|lue} after\"", pasted))
        assertEquals("\\\"\\n\\${'$'}", transformAt("val text = \"before ${'$'}{value} af|ter\"", pasted))
        assertEquals(pasted, transformAt("val text = \"before ${'$'}va|lue after\"", pasted))
    }

    @Test
    fun `raw strings preserve raw characters and protect interpolation and closing quotes`() {
        val pasted = "path\\file\n${'$'}value \"\"\" end"
        val expected = buildString {
            append("path\\file\n")
            appendRawCharacterExpressionForTest('$')
            append("value ")
            appendRawCharacterExpressionForTest('"')
            append("\"\" end")
        }

        assertEquals(expected, transformAt("val text = \"\"\"be|fore\"\"\"", pasted))
    }

    @Test
    fun `paste transforms each caret according to its own context`() {
        val document = "val text = \"\"; val value = unit"
        val insideString = document.indexOf("\"\"") + 1
        val state = TextFieldState(
            initialText = document,
            multiline = true,
            pasteTransformer = KotlinStringPasteTransformer,
        )
        state.setCarets(listOf(UiTextCaret(insideString), UiTextCaret(document.length)))

        assertTrue(state.paste("\""))
        assertEquals("val text = \"\\\"\"; val value = unit\"", state.text)
    }

    @Test
    fun `string escaping is enabled only for Kotlin sources`() {
        assertTrue("scripts/dialogue.node.kts".isKotlinSource())
        assertTrue("Generated.kt".isKotlinSource())
        assertFalse("config.json".isKotlinSource())
        assertFalse("notes.txt".isKotlinSource())
    }

    private fun transformAt(documentWithCaret: String, pastedText: String): String {
        val caret = documentWithCaret.indexOf('|')
        require(caret >= 0)
        val document = documentWithCaret.removeRange(caret, caret + 1)
        return KotlinStringPasteTransformer.transform(document, UiTextCaret(caret), pastedText)
    }

    private fun StringBuilder.appendRawCharacterExpressionForTest(char: Char) {
        append('$')
        append('{')
        append('\'')
        append(char)
        append('\'')
        append('}')
    }
}
