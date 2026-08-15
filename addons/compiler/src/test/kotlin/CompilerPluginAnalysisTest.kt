import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import org.jetbrains.kotlin.fir.FirSession
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentImpl
import ru.hollowhorizon.hollowengine.common.scripting.ScriptClassProvider
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

abstract class CompilerPluginAnalysisScript

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompilerPluginAnalysisTest {
    private val fixture = AnalysisEnvironmentTestFixture {
        ScriptingEnvironmentImpl(
            javaHome = File(System.getProperty("java.home")),
            classpath = testClasspath(),
            scriptTypes = listOf(
                ScriptClassProvider(
                    extension = ".analysis.kts",
                    baseClass = CompilerPluginAnalysisScript::class.qualifiedName!!,
                ),
                ScriptClassProvider(
                    extension = "ui.kts",
                    baseClass = "ru.hollowhorizon.hollowengine.common.scripting.ui.UiScript",
                ),
            ),
            mappings = Mappings.EMPTY,
        )
    }

    @BeforeAll
    fun startEnvironment() = fixture.start()

    @AfterEach
    fun resetEnvironment() = fixture.reset()

    @AfterAll
    fun closeEnvironment() = fixture.close()

    @Test
    fun `analysis understands compose function types and call context`() {
        withEnvironment { environment ->
            assertNoErrors(
                environment.analyzer.diagnostic(
                    "compose.analysis.kts",
                    """
                        @androidx.compose.runtime.Composable
                        fun Demo() {
                            androidx.compose.runtime.key(1) { }
                        }
                    """.trimIndent(),
                )
            )

            val contextErrors = environment.analyzer.diagnostic(
                "invalid-compose-context.analysis.kts",
                """
                    @androidx.compose.runtime.Composable
                    fun Child() = Unit

                    fun InvalidCaller() {
                        Child()
                    }
                """.trimIndent(),
            ).filter { it.severity.isError() }
            assertTrue(
                contextErrors.any { it.message.contains("Composable invocations can only happen") },
                contextErrors.toString(),
            )
        }
    }

    @Test
    fun `analysis resolves the real ui composable receiver`() {
        withEnvironment { environment ->
            val code = """
                screen("test:screen") {
                    content {
                        val serverBound = isServerBound
                    }
                }
            """.trimIndent()
            assertNoErrors(environment.analyzer.diagnostic("screen.ui.kts", code))

            val completionCode = """
                screen("test:completion") {
                    content {
                        isSer<caret>
                    }
                }
            """.trimIndent()
            val caret = completionCode.indexOf("<caret>")
            val source = completionCode.replace("<caret>", "")
            val completions = environment.analyzer.completions(
                "completion.ui.kts",
                source,
                caret,
            )
            assertTrue(
                completions.any { it.name == "isServerBound" },
                completions.toString(),
            )
        }
    }

    @Test
    fun `binary and resolvable libraries keep compatible plugin providers`() {
        withEnvironment { environment ->
            val analyzer = environment.analyzer
            val library = analyzer.libraries.single { !it.isSdk }
            val (resolvableSession, binarySession) = ApplicationManager.getApplication().runReadAction(
                Computable {
                    CompilerPluginSessionTestBridge.getSession(
                        analyzer.project,
                        library,
                        false,
                    ) to CompilerPluginSessionTestBridge.getSession(
                        analyzer.project,
                        library,
                        true,
                    )
                },
            )

            assertEquals(FirSession.Kind.Source, resolvableSession.kind)
            assertFalse(CompilerPluginSessionTestBridge.hasBinaryLibraryProvider(resolvableSession))
            assertTrue(CompilerPluginSessionTestBridge.hasComposeFunctionTypeKind(resolvableSession))

            assertEquals(FirSession.Kind.Library, binarySession.kind)
            assertTrue(CompilerPluginSessionTestBridge.hasBinaryLibraryProvider(binarySession))
            assertTrue(CompilerPluginSessionTestBridge.hasComposeFunctionTypeKind(binarySession))
        }
    }

    @Test
    fun `analysis exposes serialization generated declarations`() {
        withEnvironment { environment ->
            val code = """
                @kotlinx.serialization.Serializable
                data class Payload(val value: String)

                val payloadSerializer = Payload.serializer()
            """.trimIndent()
            assertNoErrors(environment.analyzer.diagnostic("serialization.analysis.kts", code))

            val completionCode = """
                @kotlinx.serialization.Serializable
                data class CompletionPayload(val value: String)

                val payloadSerializer = CompletionPayload.ser<caret>
            """.trimIndent()
            val caret = completionCode.indexOf("<caret>")
            val source = completionCode.replace("<caret>", "")
            val completions = environment.analyzer.completions(
                "serialization-completion.analysis.kts",
                source,
                caret,
            )
            assertTrue(
                completions.any { it.name == "serializer" },
                completions.toString(),
            )
        }
    }

    private fun withEnvironment(block: (ScriptingEnvironmentImpl) -> Unit) {
        block(fixture.environment)
    }

    private fun assertNoErrors(diagnostics: List<Diagnostic>) {
        val errors = diagnostics.filter { it.severity.isError() }
        assertTrue(errors.isEmpty(), errors.toString())
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
