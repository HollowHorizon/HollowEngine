import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.compiler.JvmHacks
import ru.hollowhorizon.hollowengine.compiler.coroutine.SuspendLauncher
import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFunction0
import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFunction1
import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFunction2
import ru.hollowhorizon.hollowengine.scripting.ResumeState
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class LambdaTests {
    val TEST_DIR = File("src/test/transforms/lambdas")

    @Test
    fun `Lambda Suspendable Function (One Call)`() {
        val coroutine = makeCoroutine("LambdaSuspendableFunction") as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "First call did not return Unit!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
        assertTrue(serialized.contains("lambda"), "Serialized output must contain 'lambda' field!")
    }

    @Test
    fun `Lambda Suspendable Function (One Call With Locals)`() {
        val coroutine = makeCoroutine("LocalsLambdaSuspendableFunction", params = "kotlin_String") as SFunction1<String, Any?>
        fun SFunction1<String, Any?>.update(branch: String): Any? {
            var result: Any?
            do {
                result = this(branch)
            } while (result == ResumeState)
            return result
        }

        assertEquals("Hello, User: Halva", coroutine.update("Halva"), "First call did not return Unit!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
        assertTrue(serialized.contains("lambda"), "Serialized output must contain 'lambda' field!")
        assertTrue(serialized.contains("prefix"), "Serialized output must contain 'prefix' field!")
    }

    @Test
    fun `Lambda Suspendable Function (One Call With Lambda Parameter)`() {
        val coroutine = makeCoroutine("LambdaParameterSuspendableFunction") as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "First call did not return Unit!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
        assertTrue(serialized.contains("lambda"), "Serialized output must contain 'lambda' field!")
    }

    @Test
    fun `Lambda Suspendable Function (Inner Calls)`() {
        val coroutine = makeCoroutine("InnerLambdaSuspendableFunction") as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "First call did not return Unit!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
        assertTrue(serialized.contains("lambda"), "Serialized output must contain 'lambda' field!")
        assertTrue(serialized.contains("data"), "Serialized output must contain 'data' field!")

    }


    @Suppress("UNCHECKED_CAST")
    private fun <T> makeCoroutine(name: String, params: String = "", decompile: Boolean = false): T {
        val file = TEST_DIR.resolve("$name.kt")

        val result = compile(SourceFile.kotlin(file.name, file.readText()))

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        if(decompile) CfrHelper.decompile(result)

        return result.classLoader.loadCoroutine("test$params") as T
    }

}