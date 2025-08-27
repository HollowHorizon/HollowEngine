package ru.hollowhorizon.hc.common.events.registry

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import ru.hollowhorizon.hc.common.events.Event

class RegisterCommandsEvent(
    val dispatcher: CommandDispatcher<CommandSourceStack>,
    val registryAccess: CommandBuildContext,
    val environment: Commands.CommandSelection,
) : Event

class RegisterClientCommandsEvent(
    val dispatcher: CommandDispatcher<SharedSuggestionProvider>,
    val registryAccess: CommandBuildContext,
) : Event