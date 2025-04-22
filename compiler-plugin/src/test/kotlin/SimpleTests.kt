import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
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
class SimpleTests {
    val TEST_DIR = File("src/test/transforms/simple")

    @Test
    fun `Simple Suspendable Function (One Call)`() {
        val coroutine = makeCoroutine("SimpleSuspendableFunction") as SFunction0<Any?>

        val result1 = coroutine()
        assertEquals(Unit, result1, "First call did not return Unit!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
    }

    @Test
    fun `Simple Suspendable Function (Several Calls)`() {
        val coroutine = makeCoroutine("SeveralCallsSuspendableFunction") as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "Suspend call did not return Unit!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
    }

    @Test
    fun `Simple Suspendable Function (Return Value)`() {
        val coroutine = makeCoroutine("ReturnSuspendableFunction") as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals("Result: 1", launcher.update(), "Suspend call did not return \"Result: 1\"!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
    }

    @Test
    fun `Simple Suspendable Function (Local variables)`() {
        val coroutine = makeCoroutine("LocalsSuspendableFunction") as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "Suspend call did not return Unit!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
        assertTrue(serialized.contains("\"before\": 10"), "Serialized output must contain 'before' variable with value 10!")
        assertTrue(serialized.contains("\"after\": \"There is nothing\""), "Serialized output must contain 'after' variable with value \"There is nothing\"!")
        assertFalse(serialized.contains("unusued"), "Compiler must ignore 'unusued' variable, because it used only in one state")
    }

    @Test
    fun `Simple Suspendable Function (Function parameters)`() {
        val coroutine = makeCoroutine("ParametrizedSuspendableFunction", params = "kotlin_String_kotlin_String") as SFunction2<String, String, Any?>
        fun SFunction2<String, String, Any?>.update(first: String, second: String): Any? {
            var result: Any?
            do {
                result = this(first, second)
            } while (result == ResumeState)
            return result
        }

        assertEquals(Unit, coroutine.update("Halva", "halva@example.com"), "Suspend call did not return Unit!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
    }

    @Test
    fun `Simple Suspendable Function (Several returns)`() {
        val coroutine = makeCoroutine("SeveralReturnSuspendableFunction", params = "Z") as SFunction1<Boolean, Any?>
        fun SFunction1<Boolean, Any?>.update(isFirst: Boolean): Any? {
            var result: Any?
            do {
                result = this(isFirst)
            } while (result == ResumeState)
            return result
        }

        val initialState = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertEquals("First", coroutine.update(isFirst = true), "Suspend call did not return 'First'!")
        json.decodeFromString(coroutine.serializer, JvmHacks.forceCast(initialState)) // Reset coroutine
        assertEquals("Second", coroutine.update(isFirst = false), "Suspend call did not return 'Second'!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
    }

    @Test
    fun `Simple Suspendable Function (Polymorphic)`() {
        val coroutine = makeCoroutine("PolymorphicSuspendableFunction") as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "Suspend call did not return Unit!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
    }

    @Test
    fun `Simple Suspendable Function (Inline)`() {
        val coroutine = makeCoroutine("InlineSuspendableFunction", decompile = true) as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "Suspend call did not return Unit!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
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