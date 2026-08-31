import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ScriptTextTests {
    @Test
    fun `line endings, indentation and blank lines normalize away`() {
        assertSameCode("val a = 1\nval b = 2", "val a = 1\r\nval b = 2")
        assertSameCode("val a = 1\nval b = 2", "    val a = 1\n\n\n\tval b = 2\n")
        assertSameCode("val a = 1", "\n\n  val a = 1  \n\n")
    }

    @Test
    fun `comments normalize away but still separate what they stood between`() {
        assertSameCode("val a = 1", "val a = 1 // a comment")
        assertSameCode("val a = 1", "// leading\nval a = 1\n/* trailing */")
        assertSameCode("val a = 1", "val /* here */ a = 1")
        assertSameCode("val a = 1", "val /* nested /* deeper */ still */ a = 1")

        assertEquals("val a", ScriptText.normalize("val/* */a"))
    }

    @Test
    fun `a newline survives as a newline because Kotlin ends statements at one`() {
        assertEquals("val a = 1\nval b = 2", ScriptText.normalize("val a = 1\nval b = 2"))
        assertNotEquals(ScriptText.normalize("val a = 1\nval b = 2"), ScriptText.normalize("val a = 1 val b = 2"))

        assertEquals("val a = 1\nval b = 2", ScriptText.normalize("val a = 1 // why\nval b = 2"))
    }

    @Test
    fun `spacing and comment markers inside literals are content, not formatting`() {
        assertNotEquals(ScriptText.normalize("val a = \"x  y\""), ScriptText.normalize("val a = \"x y\""))
        assertUnchanged("val a = \"// not a comment\"")
        assertUnchanged("val a = \"/* nor this */\"")
        assertUnchanged("val a = '\"'")

        assertUnchanged("val a = \"\\\"  x\"")
    }

    @Test
    fun `a raw string keeps its layout`() {
        val raw = "val a = $TRIPLE\n    line one\n    line two\n$TRIPLE"
        assertUnchanged(raw)
        assertNotEquals(ScriptText.normalize(raw), ScriptText.normalize(raw.replace("    line two", "line two")))

        assertUnchanged("val a = ${TRIPLE}x  y$TRIPLE\"")
    }

    @Test
    fun `a string template holds code, and code inside it normalizes`() {
        assertSameCode(
            """val a = "${'$'}{ b + c }"""",
            """val a = "${'$'}{ b  +  /* here */ c }"""",
        )
        val nested = """val a = "${'$'}{map["key"]}  tail""""
        assertEquals(nested, ScriptText.normalize(nested))
    }

    @Test
    fun `a backtick name is a name, whatever it is spelled with`() {
        assertUnchanged("val `a\"b` = 1")
        assertUnchanged("val `a//b` = 1")
        assertUnchanged("fun `it does not \$compile`() = 1")

        assertSameCode("val `odd name` = 1", "val `odd name`  =  1 // yes")
    }

    @Test
    fun `a comment inside a string template is still a comment`() {
        assertSameCode(
            "val a = \"\${ b }\"",
            "val a = \"\${ /* pointless */ b }\"",
        )
    }

    @Test
    fun `an unterminated literal does not swallow the rest of the file`() {
        assertEquals("val a = \"oops\nval b = 2", ScriptText.normalize("val a = \"oops\nval b = 2"))
    }

    private fun assertSameCode(expected: String, actual: String) {
        assertEquals(ScriptText.normalize(expected), ScriptText.normalize(actual))
    }

    private fun assertUnchanged(text: String) {
        assertEquals(text, ScriptText.normalize(text))
    }

    private companion object {
        const val TRIPLE = "\"\"\""
    }
}
