import ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentImpl
import ru.hollowhorizon.hollowengine.common.scripting.ScriptClassProvider
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType
import java.io.File
import kotlin.test.Test
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
