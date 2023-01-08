package ru.hollowhorizon.hollowstory.common.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.command.CommandSource
import net.minecraft.command.Commands

fun buildStringCommand(name: String, action: (CommandSource, String) -> Unit): LiteralArgumentBuilder<CommandSource> {
    return Commands.literal(name).then(Commands.argument("string", StringArgumentType.greedyString()).executes {

        action(it.source, StringArgumentType.getString(it, "string"))
        1
    })
}