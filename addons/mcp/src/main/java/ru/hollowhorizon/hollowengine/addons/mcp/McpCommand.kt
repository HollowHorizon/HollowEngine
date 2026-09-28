package ru.hollowhorizon.hollowengine.addons.mcp

import com.mojang.brigadier.tree.CommandNode
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import ru.hollowhorizon.hollowengine.addons.mcp.client.copyToClipboard
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient

/**
 * `/he mcp` shows whether the server runs and offers the connection for each [AgentClient];
 * `/he mcp copy <client>` puts it on the clipboard. The connection carries the token, so it never
 * appears in chat or in the log: only the host of a local world can copy it, straight into this
 * game's clipboard, since the server listens on this computer alone.
 */
internal class McpCommand(private val server: () -> McpServer?, private val status: () -> Component) {
    fun register(root: CommandNode<CommandSourceStack>) {
        val copy = Commands.literal("copy")
        AgentClient.entries.forEach { client ->
            copy.then(Commands.literal(client.id).executes { context -> copy(context.source, client) })
        }
        val command = Commands.literal("mcp")
            .requires { source -> isHost(source) || source.hasPermission(4) }
            .executes { context -> show(context.source) }
            .then(copy)
        root.addChild(command.build())
    }

    private fun show(source: CommandSourceStack): Int {
        val mcp = server()
        val url = mcp?.url
        val rows = buildList {
            add(title(running = url != null, url))
            when {
                url == null -> add(status().copy().withStyle(ChatFormatting.GRAY))
                isHost(source) -> {
                    add(text("copy", "Copy the connection for your agent:").withStyle(ChatFormatting.GRAY))
                    add(buttons())
                }
                else -> add(
                    text(
                        "elsewhere",
                        "Agents connect on the machine this game runs on, with the token from config/hollowengine-mcp.toml",
                    ).withStyle(ChatFormatting.GRAY)
                )
            }
        }
        source.sendSystemMessage(panel(rows))
        return if (url != null) 1 else 0
    }

    private fun copy(source: CommandSourceStack, client: AgentClient): Int {
        if (!isHost(source)) {
            source.sendFailure(text("host_only", "Only the host of a local world can copy the connection: agents reach it on this computer alone"))
            return 0
        }
        val url = server()?.url ?: return 0.also { source.sendFailure(status()) }
        copyToClipboard(client.connection(url, McpConfig.token))
        source.player?.displayClientMessage(
            text("copied", "Copied the connection for %s", client.title).withStyle(ChatFormatting.GREEN),
            true,
        )
        return 1
    }

    /** Whether [source] is the player hosting this very game's world, whose clipboard is this computer's. */
    private fun isHost(source: CommandSourceStack): Boolean {
        if (!isPhysicalClient) return false
        val player = source.player ?: return false
        return source.server.isSingleplayerOwner(player.gameProfile)
    }

    private fun title(running: Boolean, url: String?): MutableComponent {
        val state = if (running) text("running", "running") else text("stopped", "stopped")
        return Component.literal("HollowEngine MCP").withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE)
            .append(Component.literal("  ● ").withStyle(if (running) ChatFormatting.GREEN else ChatFormatting.RED))
            .append(state.withStyle(if (running) ChatFormatting.GREEN else ChatFormatting.RED))
            .apply { if (url != null) append(Component.literal("  $url").withStyle(ChatFormatting.DARK_GRAY)) }
    }

    private fun buttons(): MutableComponent {
        val row = Component.empty()
        AgentClient.entries.forEachIndexed { index, client ->
            if (index > 0) row.append(Component.literal("  "))
            val hint = Component.translatableWithFallback(client.hintKey, client.hint)
                .append(Component.literal("\n"))
                .append(text("hint_token", "Carries the access token").withStyle(ChatFormatting.DARK_GRAY))
            row.append(
                Component.literal("[ ${client.title} ]").withStyle { style ->
                    style.withColor(client.color).withBold(true)
                        .withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hollowengine mcp copy ${client.id}"))
                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hint))
                }
            )
        }
        return row
    }

    /** Rows set off from the rest of the chat by an accent bar down their left edge. */
    private fun panel(rows: List<Component>): Component {
        val panel = Component.empty()
        rows.forEachIndexed { index, row ->
            if (index > 0) panel.append(Component.literal("\n"))
            panel.append(Component.literal("▍ ").withColor(ACCENT)).append(row)
        }
        return panel
    }

    private fun text(key: String, fallback: String, vararg args: Any): MutableComponent =
        Component.translatableWithFallback("hollowengine_mcp.command.$key", fallback, *args)

    private companion object {
        const val ACCENT = 0x4C86E0
    }
}
