import ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentImpl
import ru.hollowhorizon.hollowengine.common.scripting.ScriptClassProvider
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class AnalysisScript

class ScriptingAnalysisEnvironmentTest {
    @Test
    fun `diagnostic reports type errors through analysis api`() {
        withEnvironment { environment ->
            val diagnostics = environment.analyzer.diagnostic(
                "broken.analysis.kts",
                """val number: Int = "not a number"""",
            )

            assertTrue(diagnostics.any { it.severity.isError() }, diagnostics.toString())
        }
    }

    @Test
    fun `highlight resolves basic kotlin tokens through analysis api`() {
        withEnvironment { environment ->
            val lines = environment.analyzer.highlight(
                "highlight.analysis.kts",
                """
                    val answer = 42
                    answer
                """.trimIndent(),
                4,
            )

            val spans = lines.flatMap { line -> line.spans }

            assertTrue(spans.any { (text, style) -> text == "val" && style.color == TokenType.KEYWORD }, spans.toString())
            assertTrue(spans.any { (text, style) -> text == "42" && style.color == TokenType.NUMERIC_LITERAL }, spans.toString())
        }
    }

    @Test
    fun `highlight keeps inlay hints after leading newline`() {
        withEnvironment { environment ->
            val text = "\nval answer = 42"

            val lines = environment.analyzer.highlight("leading-newline.analysis.kts", text, text.length)

            val secondLineHints = lines.getOrNull(1)?.hints.orEmpty()
            assertTrue(secondLineHints.any { it.index == "val answer".length }, secondLineHints.toString())
        }
    }

    @Test
    fun `highlight selects the matching brace pair under caret`() {
        withEnvironment { environment ->
            val text = "fun run() { repeat(3) {} }"
            val innerOpen = text.indexOf("{}")
            val lines = environment.analyzer.highlight(
                "brace-pair.analysis.kts",
                text,
                innerOpen + 1,
            )

            val spans = lines.flatMap { line -> line.spans }
            val highlightedBraces = spans.count { (segment, style) ->
                (segment == "{" || segment == "}") && style.highlight
            }
            assertEquals(2, highlightedBraces, spans.toString())
        }
    }

    @Test
    fun `highlight without caret does not select the first brace pair`() {
        withEnvironment { environment ->
            val text = "fun run() { repeat(3) {} }"
            val full = environment.analyzer.highlight(
                "no-caret-full.analysis.kts",
                text,
                -1,
            )

            assertFalse(full.flatMap { it.spans }.any { (_, style) -> style.highlight }, full.toString())
        }
    }

    @Test
    fun `occurrences returns all usages of the variable under caret`() {
        withEnvironment { environment ->
            val text = "val answer = 42\nval copy = answer + answer"
            val ranges = environment.analyzer.occurrences(
                "occurrences.analysis.kts",
                text,
                text.indexOf("answer") + 2,
            )

            val expected = listOf(
                text.indexOf("answer"),
                text.indexOf("answer", text.indexOf("copy")),
                text.lastIndexOf("answer"),
            ).map { start -> start to start + "answer".length }

            assertEquals(expected, ranges.map { it.start to it.end }, ranges.toString())
        }
    }

    @Test
    fun `occurrences returns matching bracket pair`() {
        withEnvironment { environment ->
            val text = "fun run() { repeat(3) {} }"
            val innerOpen = text.indexOf("{}")
            val ranges = environment.analyzer.occurrences(
                "occurrences-brackets.analysis.kts",
                text,
                innerOpen + 1,
            )

            assertEquals(
                listOf(innerOpen to innerOpen + 1, innerOpen + 1 to innerOpen + 2),
                ranges.map { it.start to it.end },
                ranges.toString(),
            )
        }
    }

    @Test
    fun `occurrences inside comment is empty`() {
        withEnvironment { environment ->
            val text = "// comment\nval answer = 42"
            val ranges = environment.analyzer.occurrences("occurrences-comment.analysis.kts", text, 3)

            assertTrue(ranges.isEmpty(), ranges.toString())
        }
    }

    @Test
    fun `caret inside comment does not highlight the comment`() {
        withEnvironment { environment ->
            val text = "// comment\nval answer = 42"
            val lines = environment.analyzer.highlight(
                "comment-caret.analysis.kts",
                text,
                3,
            )

            assertFalse(lines.flatMap { it.spans }.any { (_, style) -> style.highlight }, lines.toString())
        }
    }

    @Test
    fun `full highlight resolves edited declarations and inlay hints`() {
        withEnvironment { environment ->
            val text = "val answer = 42\nval copy = answer"
            val lines = environment.analyzer.highlight(
                "edited-full.analysis.kts",
                text,
                text.lastIndexOf("answer"),
            )

            val spans = lines.flatMap { line -> line.spans }
            val secondLineHints = lines.getOrNull(1)?.hints.orEmpty()

            assertTrue(
                spans.any { (segment, style) -> segment == "answer" && style.color in setOf(TokenType.VARIABLE, TokenType.PROPERTY_IDENTIFIER) && style.highlight },
                spans.toString(),
            )
            assertTrue(
                secondLineHints.any { hint -> hint.index == "val copy".length && hint.text.contains("Int") },
                secondLineHints.toString(),
            )
        }
    }

    @Test
    fun `diagnostic resolves declarations from imported scripts`() {
        withEnvironment { environment ->
            writeSandboxScript("shared.analysis.kts", "val importedValue = 21")
            writeSandboxScript("other.analysis.kts", "val otherValue = 21")
            val text = """
                @file:Import("shared.analysis.kts", "other.analysis.kts")
                val answer = importedValue + otherValue
            """.trimIndent()

            val diagnostics = environment.analyzer.diagnostic("scripts/main.analysis.kts", text)

            assertFalse(
                diagnostics.any { diagnostic ->
                    diagnostic.message.contains("importedValue") || diagnostic.message.contains("otherValue")
                },
                diagnostics.toString(),
            )
        }
    }

    @Test
    fun `highlight provides hints for declarations using imported scripts`() {
        withEnvironment { environment ->
            writeSandboxScript("shared.analysis.kts", "val importedValue = 21")
            val text = """
                @file:Import("shared.analysis.kts")
                val answer = importedValue * 2
            """.trimIndent()

            val lines = environment.analyzer.highlight("scripts/main.analysis.kts", text, text.length)

            val answerHints = lines.getOrNull(1)?.hints.orEmpty()
            assertTrue(
                answerHints.any { hint -> hint.index == "val answer".length && hint.text.contains("Int") },
                answerHints.toString(),
            )
        }
    }

    @Test
    fun `missing imports report diagnostics without breaking hints`() {
        withEnvironment { environment ->
            val text = """
                @file:Import("missing.analysis.kts")
                val answer = 42
            """.trimIndent()

            val diagnostics = environment.analyzer.diagnostic("scripts/main.analysis.kts", text)
            val lines = environment.analyzer.highlight("scripts/main.analysis.kts", text, text.length)

            assertTrue(
                diagnostics.any { it.severity.isError() && it.message.contains("missing.analysis.kts") },
                diagnostics.toString(),
            )
            assertTrue(
                lines.getOrNull(1)?.hints.orEmpty().any { hint ->
                    hint.index == "val answer".length && hint.text.contains("Int")
                },
                lines.toString(),
            )
        }
    }

    @Test
    fun `recursive imports report diagnostics without breaking hints`() {
        withEnvironment { environment ->
            val first = """
                @file:Import("second.analysis.kts")
                val firstValue = secondValue
            """.trimIndent()
            writeSandboxScript("first.analysis.kts", first)
            writeSandboxScript(
                "second.analysis.kts",
                """
                    @file:Import("first.analysis.kts")
                    val secondValue = 42
                """.trimIndent(),
            )

            val diagnostics = environment.analyzer.diagnostic("scripts/first.analysis.kts", first)
            val lines = environment.analyzer.highlight("scripts/first.analysis.kts", first, first.length)

            assertTrue(
                diagnostics.any { it.severity.isError() && it.message.contains("cycle") },
                diagnostics.toString(),
            )
            assertTrue(
                lines.getOrNull(1)?.hints.orEmpty().any { hint ->
                    hint.index == "val firstValue".length && hint.text.contains("Int")
                },
                lines.toString(),
            )
        }
    }

    @Test
    fun `completion sees declarations from current script`() {
        withEnvironment { environment ->
            val text = """
                val localValue = 1
                loc
            """.trimIndent()
            val completions = environment.analyzer.completions(
                "completion.analysis.kts",
                text,
                text.length,
            )

            assertTrue(completions.any { it.name == "localValue" }, completions.toString())
        }
    }

    private fun withEnvironment(block: (ScriptingEnvironmentImpl) -> Unit) {
        val environment = ScriptingEnvironmentImpl(
            javaHome = File(System.getProperty("java.home")),
            classpath = testClasspath(),
            scriptTypes = listOf(
                ScriptClassProvider(
                    extension = ".analysis.kts",
                    baseClass = AnalysisScript::class.qualifiedName!!,
                )
            ),
            mappings = Mappings.EMPTY,
        )

        try {
            block(environment)
        } finally {
            environment.close()
            File("hollowengine").deleteRecursively()
        }
    }

    private fun writeSandboxScript(path: String, text: String) {
        File("hollowengine/scripts", path).apply {
            parentFile.mkdirs()
            writeText(text)
        }
    }

    private fun testClasspath(): List<File> {
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .asSequence()
            .filter(String::isNotBlank)
            .map(::File)
            .filter(File::exists)
            .distinctBy { it.absoluteFile.normalize() }
            .toList()
    }
}
