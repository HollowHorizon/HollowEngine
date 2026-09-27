package ru.hollowhorizon.hollowengine.addons.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.coroutines.withContext
import ru.hollowhorizon.hollowengine.addons.mcp.COMPILER_MISSING
import ru.hollowhorizon.hollowengine.addons.mcp.McpTool
import ru.hollowhorizon.hollowengine.addons.mcp.ParameterType
import ru.hollowhorizon.hollowengine.addons.mcp.ToolInputException
import ru.hollowhorizon.hollowengine.addons.mcp.ToolParameter
import ru.hollowhorizon.hollowengine.addons.mcp.errorResult
import ru.hollowhorizon.hollowengine.addons.mcp.textResult
import ru.hollowhorizon.hollowengine.addons.mcp.truncate
import ru.hollowhorizon.hollowengine.client.scripting.ConsoleScripts
import ru.hollowhorizon.hollowengine.common.coroutines.dispatcher
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.console.ServerConsoleScripts
import ru.hollowhorizon.hollowengine.common.scripting.console.SnippetRun
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptCompilationException
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient

internal fun runSnippetTool() = McpTool(
    name = "run_snippet",
    description = """
        Compiles and runs a Kotlin snippet inside the game, like the IDE console, and returns the lines it
        printed with println() and the value of its last expression. Use it to inspect live game state or
        to try an API before writing it into a script. It is not how features are built: what a snippet
        does is gone after a restart, so behavior the user asked for goes into a script file.
        side=server runs on the server thread with `server`, `overworld`, `players` and `player` (the
        host, or the only player) in scope. It needs an open world where the agent may run code: a
        dedicated server, or a local world whose host has cheats on.
        side=client runs on the render thread with `minecraft`, `player`, `level` and `server` (the
        integrated one, touch it via server?.execute { }) in scope; it also works in the main menu.
        Without side: server when allowed, client otherwise. Coroutines started with launch { } keep
        running after the snippet returns; what they print only reaches the game log.
        Common Minecraft types are imported already; add other imports as lines at the top.
    """.trimIndent(),
    parameters = listOf(
        ToolParameter("code", ParameterType.STRING, "Kotlin statements; the last expression is the result", required = true),
        ToolParameter("side", ParameterType.STRING, "Where to run it", allowed = listOf(SERVER, CLIENT)),
    ),
    readOnly = false,
) { arguments ->
    val code = arguments.string("code")
    if (!ScriptingEnvironment.isAvailable()) return@McpTool errorResult(COMPILER_MISSING)
    when (resolveSide(arguments.optionalString("side"))) {
        SERVER -> {
            val server = ServerAccess.current() ?: return@McpTool errorResult(ServerAccess.NO_SERVER)
            report(SERVER, ServerConsoleScripts.evaluate(server, code))
        }
        else -> report(CLIENT, ConsoleScripts.evaluate(code))
    }
}

private suspend fun resolveSide(requested: String?): String {
    val server = ServerAccess.current()
    val serverAllowed = server != null && withContext(server.dispatcher) { ServerAccess.mayRunCode(server) }
    return when (requested) {
        SERVER -> when {
            server == null -> throw ToolInputException(ServerAccess.NO_SERVER)
            !serverAllowed -> throw ToolInputException(ServerAccess.NO_CHEATS)
            else -> SERVER
        }
        CLIENT -> if (isPhysicalClient) CLIENT else throw ToolInputException("This is a dedicated server; it has no client side")
        null -> if (serverAllowed || !isPhysicalClient) SERVER else CLIENT
        else -> throw ToolInputException("'side' must be '$SERVER' or '$CLIENT'")
    }
}

private fun report(side: String, run: SnippetRun): CallToolResult {
    val error = run.result.exceptionOrNull()
    val text = buildString {
        appendLine("Ran on the $side.")
        if (run.output.isNotEmpty()) {
            appendLine("Printed:")
            run.output.forEach(::appendLine)
        }
        run.result.onSuccess { result ->
            if (result.hasValue) appendLine("Result: ${runCatching { result.value.toString() }.getOrElse { "<toString() threw $it>" }}")
        }
        when (error) {
            null -> Unit
            is ScriptCompilationException -> {
                appendLine("Did not compile:")
                error.reports.filter { it.severity.isError() }.forEach { report ->
                    val start = report.range.start
                    appendLine(if (start.line < 0) report.message else "${start.line + 1}:${start.column + 1}: ${report.message}")
                }
            }
            else -> {
                appendLine("Threw:")
                append(error.stackTraceToString().lineSequence().take(MAX_TRACE_LINES).joinToString("\n"))
            }
        }
    }.trimEnd()
    return if (error == null) textResult(truncate(text)) else errorResult(truncate(text))
}

private const val SERVER = "server"
private const val CLIENT = "client"
private const val MAX_TRACE_LINES = 40
