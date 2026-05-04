package ru.hollowhorizon.hollowengine.common.events.registry

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

class RegisterCommandsEvent(
    val dispatcher: CommandDispatcher<CommandSourceStack>,
    val registryAccess: CommandBuildContext,
    val environment: Commands.CommandSelection,
) : Event {
    companion object : EventHandler<RegisterCommandsEvent>()
}

class RegisterClientCommandsEvent(
    val dispatcher: CommandDispatcher<SharedSuggestionProvider>,
    val registryAccess: CommandBuildContext,
) : Event {
    companion object : EventHandler<RegisterClientCommandsEvent>()
}