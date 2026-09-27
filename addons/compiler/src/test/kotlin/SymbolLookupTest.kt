import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import ru.hollowhorizon.hollowengine.common.ScriptingEnvironmentImpl
import ru.hollowhorizon.hollowengine.common.scripting.ScriptClassProvider
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.ide.SymbolKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SymbolLookupTest {
    private val fixture = AnalysisEnvironmentTestFixture {
        ScriptingEnvironmentImpl(
            javaHome = File(System.getProperty("java.home")),
            classpath = System.getProperty("java.class.path").split(File.pathSeparator)
                .filter(String::isNotBlank).map(::File).filter(File::exists).distinctBy { it.absoluteFile.normalize() },
            scriptTypes = listOf(ScriptClassProvider("kts", "kotlin.Any")),
            mappings = Mappings.EMPTY,
        )
    }

    private val analyzer get() = fixture.environment.analyzer

    @BeforeAll
    fun startEnvironment() = fixture.start()

    @AfterAll
    fun closeEnvironment() = fixture.close()

    @Test
    fun `an exact name ranks before names that only contain it`() {
        val matches = analyzer.searchSymbols("ScriptClassProvider", 20)
        assertEquals("ru.hollowhorizon.hollowengine.common.scripting.ScriptClassProvider", matches.first().qualifiedName)
        assertEquals(SymbolKind.CLASS, matches.first().kind)
    }

    @Test
    fun `extension functions show their receiver and which parameters have defaults`() {
        val match = analyzer.searchSymbols("collections.joinToString", 20)
            .first { it.kind == SymbolKind.FUNCTION && it.qualifiedName == "kotlin.collections.joinToString" }
        assertTrue(".joinToString(separator: CharSequence = …" in match.signature, match.signature)
    }

    @Test
    fun `a qualifier narrows the search to matching packages`() {
        val matches = analyzer.searchSymbols("util.ArrayList", 50)
        assertTrue(matches.isNotEmpty())
        assertTrue(matches.all { "util" in it.qualifiedName.substringBeforeLast('.') }, matches.toString())
    }

    @Test
    fun `sources resolve classes, nested classes, members and top-level functions`() {
        val member = assertNotNull(analyzer.symbolSource("java.util.ArrayList.trimToSize"))
        assertTrue(member.text!!.startsWith("trimToSize", member.offset), member.path)
        assertNotNull(analyzer.symbolSource("java.util.Map.Entry"))
        assertNotNull(analyzer.symbolSource("kotlin.collections.joinToString"))
        assertNull(analyzer.symbolSource("no.such.Thing"))
    }
}
