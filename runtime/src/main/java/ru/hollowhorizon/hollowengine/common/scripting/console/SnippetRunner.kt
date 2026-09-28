package ru.hollowhorizon.hollowengine.common.scripting.console

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.apache.logging.log4j.Logger
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.compiling.ScriptResult
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptCompilationException
import java.util.concurrent.Executors
import kotlin.script.experimental.api.constructorArgs

/**
 * What one snippet left behind: the lines it printed while it ran, and either its result or why it did
 * not produce one. A [ScriptCompilationException] means it never compiled.
 */
class SnippetRun(val output: List<String>, val result: Result<ScriptResult>)

/**
 * Compiles console snippets off the game thread and runs them on it. Every snippet is echoed to [logger]
 * along with what it printed and what it returned, so the console shows the same whoever typed it.
 */
class SnippetRunner(private val scriptName: String, private val logger: Logger, compilerThreadName: String) {
    private val compiler = Executors.newSingleThreadExecutor { task ->
        Thread(task, compilerThreadName).apply { isDaemon = true }
    }.asCoroutineDispatcher()

    /** Lines printed by the snippet running right now. Touched only on the thread snippets run on. */
    private var capture: MutableList<String>? = null

    /** Called by the snippet base classes; what a snippet prints after it returned only reaches the log. */
    fun print(line: String) {
        logger.info(line)
        capture?.add(line)
    }

    /** Compiles [code], then runs it on [gameThread] with the script constructor's [arguments]. */
    suspend fun run(code: String, gameThread: CoroutineDispatcher, arguments: () -> List<Any?>): SnippetRun {
        val environment = ScriptingEnvironment.currentOrNull()
        if (environment == null) {
            logger.warn(COMPILER_MISSING)
            return SnippetRun(emptyList(), Result.failure(IllegalStateException(COMPILER_MISSING)))
        }
        logger.info("> {}", code.trimEnd())
        val compiled = withContext(compiler) { environment.compiler.compile(scriptName, code) }
        return withContext(gameThread) {
            val output = ArrayList<String>()
            capture = output
            val result = try {
                compiled.mapCatching { script ->
                    script.evaluate { constructorArgs(*arguments().toTypedArray()) }.getOrThrow()
                }
            } finally {
                capture = null
            }
            report(result)
            SnippetRun(output, result)
        }
    }

    private fun report(result: Result<ScriptResult>) {
        result.onSuccess { value -> if (value.hasValue) logger.info("= {}", value.value) }
        val error = result.exceptionOrNull() ?: return
        val reports = (error as? ScriptCompilationException)?.reports
        if (reports == null) {
            logger.error("The snippet failed", error)
            return
        }
        reports.filter { it.severity.isError() }.forEach { report ->
            val start = report.range.start
            if (start.line < 0) logger.error(report.message)
            else logger.error("{}:{}: {}", start.line + 1, start.column + 1, report.message)
        }
    }

    companion object {
        const val COMPILER_MISSING = "Kotlin snippets need the compiler addon, which is not installed"
    }
}
