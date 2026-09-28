package ru.hollowhorizon.hollowengine.addons.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.addons.mcp.client.screenshotTool
import ru.hollowhorizon.hollowengine.addons.mcp.docs.DocsLibrary
import ru.hollowhorizon.hollowengine.addons.mcp.tools.codeTools
import ru.hollowhorizon.hollowengine.addons.mcp.tools.docsTools
import ru.hollowhorizon.hollowengine.addons.mcp.tools.runCommandTool
import ru.hollowhorizon.hollowengine.addons.mcp.tools.runSnippetTool
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonContext
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonEntrypoint
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient

class HollowMcpAddon : HollowAddonEntrypoint {
    @Volatile
    private var server: McpServer? = null

    /** Why the server is not running, when it is not. */
    @Volatile
    private var status: Component = Component.translatableWithFallback(
        "hollowengine_mcp.command.status.starting", "The MCP server has not started yet",
    )

    private val command = McpCommand(server = { server }, status = { status })

    override suspend fun load(context: HollowAddonContext, scope: CoroutineScope) {
        McpConfig.initialize()
        if (!McpConfig.enabled) {
            status = Component.translatableWithFallback(
                "hollowengine_mcp.command.status.disabled",
                "The server is off: set enabled = true in config/hollowengine-mcp.toml and restart the game",
            )
            HollowEngine.LOGGER.info("The MCP server is off in config/hollowengine-mcp.toml")
            return
        }

        val docs = DocsLibrary.load(context.classLoader, DOCS_ROOT)
        val tools = buildList {
            add(runCommandTool())
            add(runSnippetTool())
            addAll(codeTools())
            addAll(docsTools(docs))
            if (isPhysicalClient) add(screenshotTool())
        }
        val instructions = McpInstructions(context.classLoader)
        val mcp = McpServer(context.descriptor.version, tools, instructions::render)
        val port = McpConfig.port
        val started = withContext(Dispatchers.IO) {
            runCatching { mcp.start(port, McpConfig.ensureToken(), context.classLoader) }
        }
        started.onFailure { error ->
            status = Component.translatableWithFallback(
                "hollowengine_mcp.command.status.port_taken",
                "Port %s is taken; pick another in config/hollowengine-mcp.toml and restart the game",
                port,
            )
            HollowEngine.LOGGER.error("The MCP server could not listen on port {}", port, error)
            return
        }
        server = mcp
        HollowEngine.LOGGER.info("MCP server listening on {}; /he mcp copies the connection for an agent", mcp.url)
    }

    override suspend fun unload(context: HollowAddonContext) {
        withContext(Dispatchers.IO) { server?.stop() }
        server = null
    }

    @SubscribeEvent(-1)
    fun registerCommands(event: RegisterCommandsEvent) {
        val root = requireNotNull(event.dispatcher.root.getChild("hollowengine")) {
            "The HollowEngine root command must be registered before addon commands"
        }
        command.register(root)
    }

    private companion object {
        const val DOCS_ROOT = "hollowengine-mcp/docs"
    }
}
