package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

fun buildStringCommand(name: String, action: (CommandSourceStack, String) -> Unit): LiteralArgumentBuilder<CommandSourceStack> {
    return Commands.literal(name).then(Commands.argument("string", StringArgumentType.greedyString()).executes {

        action(it.source, StringArgumentType.getString(it, "string"))
        1
    })
}