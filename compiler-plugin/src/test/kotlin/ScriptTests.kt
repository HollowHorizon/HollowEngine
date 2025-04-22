import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.compiler.coroutine.SuspendLauncher
import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFunction0
import ru.hollowhorizon.hollowengine.scripting.SuspendState
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.jvm.BasicJvmScriptEvaluator
import kotlin.script.experimental.jvm.util.isError
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptTests {

    @Test
    fun `Suspendable Script`() {
        val coroutine = makeCoroutine<SFunction0<Any?>>(
            """
            println(hello)
        """.trimIndent()
        )
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "First call did not return Unit!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
    }

    private fun <T> makeCoroutine(text: String = ""): T {

        val result = compileScript<StoryEvent>(text)

        assertFalse(result.isError(), "Script failed to compile")

        val r = runBlocking { BasicJvmScriptEvaluator().invoke(result.valueOrThrow(), ScriptEvaluationConfiguration()) }


        return r.valueOrThrow().returnValue.scriptInstance as T
    }

}