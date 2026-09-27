package ru.hollowhorizon.hollowengine.addons.mcp.tools

import com.mojang.brigadier.ParseResults
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import kotlinx.coroutines.withContext
import net.minecraft.commands.CommandResultCallback
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.addons.mcp.McpTool
import ru.hollowhorizon.hollowengine.addons.mcp.ParameterType
import ru.hollowhorizon.hollowengine.addons.mcp.ToolParameter
import ru.hollowhorizon.hollowengine.addons.mcp.errorResult
import ru.hollowhorizon.hollowengine.addons.mcp.textResult
import ru.hollowhorizon.hollowengine.addons.mcp.truncate
import ru.hollowhorizon.hollowengine.common.coroutines.dispatcher

internal fun runCommandTool() = McpTool(
    name = "run_command",
    description = """
        Runs a command on this game's server and returns the feedback it printed, which would otherwise go
        to chat. In a singleplayer or LAN world it runs as the host player, with the host's rights and
        position; on a dedicated server, as the server console.
        HollowEngine's own commands live under /hollowengine (alias /he), e.g. `he scripting compile`,
        `he scripting run <path>`, `reload`. `help hollowengine` lists them; a command that does not
        parse is answered with the usage of the part that did.
    """.trimIndent(),
    parameters = listOf(
        ToolParameter("command", ParameterType.STRING, "The command, with or without the leading slash", required = true),
    ),
    readOnly = false,
) { arguments ->
    val server = ServerAccess.current() ?: return@McpTool errorResult(ServerAccess.NO_SERVER)
    withContext(server.dispatcher) { runCommand(server, arguments.string("command").trim().removePrefix("/")) }
}

private fun runCommand(server: MinecraftServer, command: String): CallToolResult {
    val source = if (server.isDedicatedServer) server.createCommandSourceStack()
    else ServerAccess.host(server)?.createCommandSourceStack()
        ?: return errorResult("The host player has not joined the world yet")

    val output = CapturedFeedback()
    var outcome: Pair<Boolean, Int>? = null
    val stack = source.withSource(output).withCallback(CommandResultCallback { success, result -> outcome = success to result })
    val dispatcher = server.commands.dispatcher
    val parse = dispatcher.parse(command, stack)
    HollowEngine.LOGGER.info("MCP agent runs /{}", command)
    server.commands.performCommand(parse, command)

    val failedToParse = parse.reader.canRead() || parse.exceptions.isNotEmpty()
    val report = buildString {
        output.lines.forEach(::appendLine)
        outcome?.let { (success, result) -> appendLine(if (success) "(result: $result)" else "(the command failed)") }
        if (output.lines.isEmpty() && outcome == null && !failedToParse) appendLine("(no feedback)")
        if (failedToParse) usage(parse, stack)?.let { usage ->
            appendLine("Usage:")
            usage.forEach { appendLine("  /$it") }
        }
    }.trimEnd()
    val failed = failedToParse || outcome?.first == false
    return if (failed) errorResult(truncate(report)) else textResult(truncate(report))
}

/** What can follow the longest part of [parse] that did parse, or null when not even the command name did. */
private fun usage(parse: ParseResults<CommandSourceStack>, stack: CommandSourceStack): List<String>? {
    val context = runCatching { parse.context.findSuggestionContext(parse.reader.cursor) }.getOrNull() ?: return null
    val dispatcher = stack.server.commands.dispatcher
    if (context.parent === dispatcher.root) return null
    val prefix = parse.reader.string.substring(0, context.startPos).trimEnd()
    return dispatcher.getSmartUsage(context.parent, stack).values.take(MAX_USAGE_LINES).map { "$prefix $it" }
}

/** A command source that keeps the feedback a command sends instead of showing it to anyone. */
private class CapturedFeedback : CommandSource {
    val lines = ArrayList<String>()

    override fun sendSystemMessage(component: Component) {
        lines += component.string
    }

    override fun acceptsSuccess(): Boolean = true

    override fun acceptsFailure(): Boolean = true

    override fun shouldInformAdmins(): Boolean = false
}

private const val MAX_USAGE_LINES = 30
