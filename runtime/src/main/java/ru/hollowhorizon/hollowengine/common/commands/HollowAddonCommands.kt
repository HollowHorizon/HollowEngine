package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonManager
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonOperationResult
import ru.hollowhorizon.hollowengine.common.utils.literal

internal fun CommandExtension.registerAddonCommands() {
    "addons" {
        "list" {
            executes {
                val statuses = HollowAddonManager.statuses
                if (statuses.isEmpty()) return@executes sendSuccess(false) { "No addons installed.".literal }
                statuses.forEach { status ->
                    val details = status.details?.let { value -> " — $value" }.orEmpty()
                    source.sendSuccess(
                        {
                            (
                                "${status.descriptor.id} ${status.descriptor.version}: " +
                                    "${status.state.name.lowercase()} [${status.fileName}]$details"
                                ).literal
                        },
                        false,
                    )
                }
                statuses.size
            }
        }

        "enable"(addonIdArgument()) {
            requires { hasPermission(2) }
            executes { report(HollowAddonManager.enable(addonId())) }
        }
        "disable"(addonIdArgument()) {
            requires { hasPermission(2) }
            executes { report(HollowAddonManager.disable(addonId())) }
        }
        "reload"(addonIdArgument()) {
            requires { hasPermission(2) }
            executes { report(HollowAddonManager.reload(addonId())) }
        }

    }
}

private fun addonIdArgument() = arg<String, CommandSourceStack>("id", StringArgumentType.word()) {
    HollowAddonManager.statuses.map { status -> status.descriptor.id }.distinct()
}

private fun com.mojang.brigadier.context.CommandContext<CommandSourceStack>.addonId(): String =
    StringArgumentType.getString(this, "id")

private fun com.mojang.brigadier.context.CommandContext<CommandSourceStack>.report(
    result: HollowAddonOperationResult,
): Int = if (result.successful) {
    sendSuccess(false) { result.message.literal }
} else {
    sendFailure(result.message.literal)
}
