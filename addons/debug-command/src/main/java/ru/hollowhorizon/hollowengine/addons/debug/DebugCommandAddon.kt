package ru.hollowhorizon.hollowengine.addons.debug

import com.mojang.brigadier.arguments.StringArgumentType
import kotlinx.coroutines.CoroutineScope
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonContext
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonEntrypoint
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterCommandsEvent

class DebugCommandAddon : HollowAddonEntrypoint {
    override suspend fun load(context: HollowAddonContext, scope: CoroutineScope) = Unit

    @SubscribeEvent(priority = -1_000)
    fun registerCommands(event: RegisterCommandsEvent) {
        val hollowEngineCommand = requireNotNull(
            event.dispatcher.root.getChild("hollowengine"),
        ) {
            "The HollowEngine root command must be registered before addon commands"
        }

        val textCommand = Commands.literal("addon-text")
            .then(
                Commands.argument<String>(
                    "text",
                    StringArgumentType.greedyString(),
                ).executes { commandContext ->
                    val text = StringArgumentType.getString(commandContext, "text")

                    commandContext.source.sendSuccess(
                        { Component.literal(text) },
                        false,
                    )

                    1
                },
            )

        hollowEngineCommand.addChild(textCommand.build())
    }
}
