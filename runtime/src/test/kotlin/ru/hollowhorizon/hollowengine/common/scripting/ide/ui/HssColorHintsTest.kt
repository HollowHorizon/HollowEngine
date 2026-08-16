package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import ru.hollowhorizon.hollowengine.common.scripting.ide.InlayAction
import ru.hollowhorizon.hollowengine.common.scripting.ide.InlayContent
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HssColorHintsTest {
    @Test
    fun `hex and function colours are both found`() {
        val literals = hssColorLiterals("1px #FF8800 rgba(10, 20, 30, 0.5)")
        assertEquals(listOf("#FF8800", "rgba(10, 20, 30, 0.5)"), literals.map { it.text })
        assertEquals(0xFFFF8800.toInt(), literals.first().argb)
    }

    @Test
    fun `values that only look like colours are skipped`() {
        assertTrue(hssColorLiterals("#GGHHII").isEmpty())
        assertTrue(hssColorLiterals("hollowengine:textures/gui/icons/link.svg").isEmpty())
    }

    @Test
    fun `alpha survives the round trip through the literal text`() {
        assertEquals("#FF8800", hssColorLiteralText(0xFFFF8800.toInt()))
        assertEquals("#FF880080", hssColorLiteralText(0x80FF8800.toInt()))
        val reparsed = hssColorLiterals(hssColorLiteralText(0x80FF8800.toInt())).single()
        assertEquals(0x80FF8800.toInt(), reparsed.argb)
    }

    @Test
    fun `a declaration colour gets a clickable swatch anchored on the literal`() {
        val source = ".panel {\n    background: #123456;\n}"
        val hints = hssInlayHints(HssDocumentModel(source))
        val swatch = hints.single { it.content.any { part -> part is InlayContent.Swatch } }
        assertEquals(source.indexOf("#123456"), swatch.index)
        val action = assertIs<InlayAction.PickColor>(InlayAction.decode(swatch.action!!.id))
        assertEquals("#123456", action.literal)
        assertEquals(source.substring(action.start, action.end), action.literal)
    }

    @Test
    fun `a task marker splits the comment it sits in`() {
        val source = "/* note TODO: fix me */\n.panel { }"
        val spans = HssLexer(source).tokenize()
        assertEquals(TokenType.COMMENT, spans.first { it.type == TokenType.COMMENT }.type)
        val todo = spans.single { it.type == TokenType.TODO_COMMENT }
        assertEquals("TODO: fix me */", source.substring(todo.start, todo.end))
    }

    @Test
    fun `a comment without a marker stays one plain span`() {
        val spans = HssLexer("// nothing to do here\n").tokenize()
        assertTrue(spans.none { it.type == TokenType.TODO_COMMENT })
    }
}
