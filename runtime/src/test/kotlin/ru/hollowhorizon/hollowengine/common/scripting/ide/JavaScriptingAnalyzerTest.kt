package ru.hollowhorizon.hollowengine.common.scripting.ide

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavaScriptingAnalyzerTest {
    @Test
    fun `block comment keeps delimiters and resumes code highlighting on the same line`() {
        val source = "int value = 1; /* explanation */ return;"
        val line = JavaScriptingAnalyzer.highlight("Example.java", source, 0).single()

        assertEquals(source, line.spans.joinToString("") { it.first })
        assertTrue(line.spans.any { (text, style) -> text == "/* explanation */" && style.color == TokenType.COMMENT })
        assertTrue(line.spans.any { (text, style) -> text == "return" && style.color == TokenType.KEYWORD })
    }

    @Test
    fun `JavaDoc comment remains highlighted across lines`() {
        val source = "/** Documentation\n * for the value\n */\nprivate int value;"
        val lines = JavaScriptingAnalyzer.highlight("Example.java", source, 0)

        assertEquals(source.lines(), lines.map { line -> line.spans.joinToString("") { it.first } })
        assertTrue(lines.take(3).all { line -> line.spans.all { it.second.color == TokenType.COMMENT } })
        assertTrue(lines.last().spans.any { (text, style) -> text == "private" && style.color == TokenType.KEYWORD })
    }
}
