import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity
import ru.hollowhorizon.hollowengine.common.scripting.ide.UnavailableKotlinScriptingAnalyzer

class ScriptingEnvironmentOptionalityTests {
    @Test
    fun `missing compiler addon leaves scripting environment unavailable`() {
        ScriptingEnvironment.clear()

        assertFalse(ScriptingEnvironment.isAvailable())
        assertEquals(null, ScriptingEnvironment.currentOrNull())
        assertFailsWith<IllegalStateException> {
            ScriptingEnvironment.INSTANCE
        }
    }

    @Test
    fun `kotlin analyzer fallback reports missing compiler addon`() {
        val diagnostics = UnavailableKotlinScriptingAnalyzer.diagnostic("test.kts", "val answer = 42")

        assertEquals(1, diagnostics.size)
        assertEquals(Severity.WARNING, diagnostics.single().severity)
        assertTrue(diagnostics.single().message.contains("compiler addon"))
    }
}
