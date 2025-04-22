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
class ControlTests {
    val TEST_DIR = File("src/test/transforms/controls")

    @Test
    fun `Control Suspendable Function (If-Else)`() {
        val coroutine = makeCoroutine("IfElseSuspendables", params = "Z") as SFunction1<Boolean, Any?>
        fun SFunction1<Boolean, Any?>.update(isFirst: Boolean): Any? {
            var result: Any?
            do {
                result = this(isFirst)
            } while (result == ResumeState)
            return result
        }

        val initialState = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertEquals(Unit, coroutine.update(isFirst = true), "Suspend call did not return 'Unit'!")
        json.decodeFromString(coroutine.serializer, JvmHacks.forceCast(initialState)) // Reset coroutine
        assertEquals(Unit, coroutine.update(isFirst = false), "Suspend call did not return 'Unit'!")

    }

    @Test
    fun `Control Suspendable Function (When)`() {
        val coroutine = makeCoroutine("WhenSuspendables", params = "I") as SFunction1<Int, Any?>
        val initialState = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        fun SFunction1<Int, Any?>.reset() =  json.decodeFromString(serializer, JvmHacks.forceCast(initialState)) // Reset coroutine
        fun SFunction1<Int, Any?>.update(branch: Int): Any? {
            var result: Any?
            do {
                result = this(branch)
            } while (result == ResumeState)
            return result
        }


        assertEquals(Unit, coroutine.update(branch = 0), "Suspend call did not return 'Unit'!")
        coroutine.reset()
        assertEquals(Unit, coroutine.update(branch = 1), "Suspend call did not return 'Unit'!")
        coroutine.reset()
        assertEquals(Unit, coroutine.update(branch = 2), "Suspend call did not return 'Unit'!")
        coroutine.reset()
        assertEquals(Unit, coroutine.update(branch = -1), "Suspend call did not return 'Unit'!")
        coroutine.reset()
        assertEquals(Unit, coroutine.update(branch = 51515351), "Suspend call did not return 'Unit'!")
    }

    @Test
    fun `Control Suspendable Function (Return + Control)`() {
        val coroutine = makeCoroutine("WhenReturnSuspendables", params = "I") as SFunction1<Int, Any?>
        val initialState = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        fun SFunction1<Int, Any?>.reset() =  json.decodeFromString(serializer, JvmHacks.forceCast(initialState)) // Reset coroutine
        fun SFunction1<Int, Any?>.update(branch: Int): Any? {
            var result: Any?
            do {
                result = this(branch)
            } while (result == ResumeState)
            return result
        }


        assertEquals(0, coroutine.update(branch = 0), "Suspend call did not return '0'!")
        coroutine.reset()
        assertEquals(1, coroutine.update(branch = 1), "Suspend call did not return '1'!")
        coroutine.reset()
        assertEquals(2, coroutine.update(branch = 2), "Suspend call did not return '2'!")
        coroutine.reset()
        assertEquals(-1, coroutine.update(branch = -1), "Suspend call did not return '-1'!")
        coroutine.reset()
        assertEquals(-404, coroutine.update(branch = 51515351), "Suspend call did not return '-404'!")
    }

    @Test
    fun `Control Suspendable Function (For)`() {
        val coroutine = makeCoroutine("ForSuspendables", decompile = true) as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "Suspend call did not return 'Unit'!")

    }

    @Test
    fun `Control Suspendable Function (While)`() {
        val coroutine = makeCoroutine("WhileSuspendables") as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "Suspend call did not return 'Unit'!")
    }

    @Test
    fun `Control Suspendable Function (Do-While)`() {
        val coroutine = makeCoroutine("DoWhileSuspendables") as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "Suspend call did not return 'Unit'!")
    }

    @Test
    fun `Control Suspendable Function (Break Continue)`() {
        val coroutine = makeCoroutine("BreakContinueSuspendables") as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "Suspend call did not return 'Unit'!")
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