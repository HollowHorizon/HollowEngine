package ru.hollowhorizon.hollowengine.client.ui.xml

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UiXmlParserTest {
    @Test
    fun `parses document directives dashed names inline nodes and quoted event scripts`() {
        val tree = parseUiXml(
            """
            <import element="hollowengine:ui/elements/badge.ui" named="badge" />
            <lazy-column id="root" offset-y="6px" enabled=true onClick='{event:"x";mouse:<it.button>}'>
                <text>Hello <b color="#ff0000">world</b><br/> &amp; <![CDATA[<raw>]]></text>
            </lazy-column>
            """.trimIndent()
        )

        assertEquals("__document", tree.name)
        assertEquals(listOf("import", "lazy-column"), tree.children.map { it.name })

        val root = tree.children[1]
        assertEquals("6px", root.attributes["offset-y"])
        assertEquals("true", root.attributes["enabled"])
        assertEquals("""{event:"x";mouse:<it.button>}""", root.attributes["onClick"])

        val text = root.children.single { it.name == "text" }
        assertEquals(listOf("#text", "b", "br", "#text", "#text"), text.children.map { it.name })
        assertEquals("Hello ", text.children[0].attributes["#text"])
        assertEquals(" & ", text.children[3].attributes["#text"])
        assertEquals("<raw>", text.children[4].attributes["#text"])
    }

    @Test
    fun `throws parse exception for mismatched closing tags`() {
        val exception = assertFailsWith<UiXmlParseException> {
            parseUiXml("<box><text>Broken</box>", "broken.ui")
        }

        assertTrue(exception.messageText.contains("Expected closing tag 'text'"))
        assertTrue(exception.messageText.contains("broken.ui"))
    }
}
