package ru.hollowhorizon.hollowengine.client.ui.style

import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.client.ui.BoxNode
import ru.hollowhorizon.hollowengine.client.ui.Modifier
import ru.hollowhorizon.hollowengine.client.ui.style
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HssVariablesTest {
    private val sheets = mapOf(
        "test:palette.hss" to """
            ${'$'}base: 0.5;
            ${'$'}half: ${'$'}base;
            .imported { opacity: 0.1; }
        """.trimIndent(),
        "test:loop-a.hss" to """@import "test:loop-b.hss"; ${'$'}a: 0.3;""",
        "test:loop-b.hss" to """@import "test:loop-a.hss"; ${'$'}b: 0.6;""",
    )

    private val reader = HssImportReader { sheets[it] }

    private fun compile(source: String): CompiledHss {
        val document = parseHss(source)
        return compileHss(document, scope = HssScope.of(document, reader))
    }

    private fun opacityOf(sheet: CompiledHss, vararg tags: String): Float {
        val child = BoxNode(tags = tags.toList())
        val root = BoxNode(modifiers = listOf(Modifier.style(sheet)))
        root.children.add(child)
        UiModifierResolver().resolve(root, animate = false)
        return child.resolvedSnapshot.opacity
    }

    @Test
    fun `a variable is written into the value, variables inside variables included`() {
        val sheet = compile("${'$'}fade: ${'$'}inner; ${'$'}inner: 0.4; .a { opacity: ${'$'}fade; }")
        assertEquals(0.4f, opacityOf(sheet, "a"))
    }

    @Test
    fun `an import brings its variables and its rules, and the importing sheet can retune them`() {
        val sheet = compile("""@import "test:palette.hss"; ${'$'}base: 0.8; .a { opacity: ${'$'}half; }""")
        assertEquals(0.8f, opacityOf(sheet, "a"))
        assertEquals(0.1f, opacityOf(sheet, "imported"))
    }

    @Test
    fun `stylesheets that import each other are each read once`() {
        val sheet = compile("""@import "test:loop-a.hss"; .a { opacity: ${'$'}b; } .b { opacity: ${'$'}a; }""")
        assertEquals(0.6f, opacityOf(sheet, "a"))
        assertEquals(0.3f, opacityOf(sheet, "b"))
    }

    @Test
    fun `a set is taken in where it is applied, and the rule's own declarations after it win`() {
        val sheet = compile(
            """
            ${'$'}faded { opacity: ${'$'}level; }
            ${'$'}level: 0.3;
            .a { @apply ${'$'}faded; }
            .b { @apply ${'$'}faded; opacity: 0.9; }
            """.trimIndent()
        )
        assertEquals(0.3f, opacityOf(sheet, "a"))
        assertEquals(0.9f, opacityOf(sheet, "b"))
    }

    @Test
    fun `a missing import and a variable that names itself fail instead of compiling to nothing`() {
        assertFailsWith<HssParseException> { compile("""@import "test:absent.hss";""") }
        assertFailsWith<IllegalArgumentException> { compile("${'$'}self: ${'$'}self; .a { opacity: ${'$'}self; }") }
    }
}
