package ru.hollowhorizon.hollowengine.common.events.registry

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.ServerEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

/** Fires on every datapack load after reload scripts have run. */
class RegisterCommandsEvent(
    val dispatcher: CommandDispatcher<CommandSourceStack>,
    val registryAccess: CommandBuildContext,
    val environment: Commands.CommandSelection,
) : ServerEvent {
    companion object : EventHandler<RegisterCommandsEvent>()
}

/** Fires whenever the client rebuilds its command tree. */
class RegisterClientCommandsEvent(
    val dispatcher: CommandDispatcher<SharedSuggestionProvider>,
    val registryAccess: CommandBuildContext,
) : ClientEvent {
    companion object : EventHandler<RegisterClientCommandsEvent>()
}
