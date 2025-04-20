import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import ru.hollowhorizon.hollowengine.compiler.coroutine.SuspendLauncher
import ru.hollowhorizon.hollowengine.compiler.coroutine.suspendable.SFunction0
import ru.hollowhorizon.hollowengine.scripting.SuspendState
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class AsyncTests {
    val TEST_DIR = File("src/test/transforms/asyncs")

    @Test
    fun `Async Suspendable Function (Basic Example)`() {
        val coroutine = makeCoroutine("AsyncSuspendable", decompile = true) as SFunction0<Any?>
        val launcher = SuspendLauncher(coroutine)

        assertEquals(Unit, launcher.update(), "First call did not return Unit!")

        val serialized = json.encodeToString(coroutine.serializer, JvmHacks.forceCast(coroutine))
        assertTrue(serialized.contains("<stateIndex>"), "Serialized output must contain <stateIndex> field!")
        assertTrue(serialized.contains("async"), "Serialized output must contain 'async' field!")
    }


    @Suppress("UNCHECKED_CAST")
    private fun <T> makeCoroutine(name: String, params: String = "", decompile: Boolean = false): T {
        val file = TEST_DIR.resolve("$name.kt")

        val result = compile(SourceFile.kotlin(file.name, file.readText()))

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode)

        if (decompile) CfrHelper.decompile(result)

        return result.classLoader.loadCoroutine("test$params") as T
    }

}