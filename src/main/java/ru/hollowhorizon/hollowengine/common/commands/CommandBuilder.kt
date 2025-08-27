/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

@file:Suppress("UNCHECKED_CAST")

package ru.hollowhorizon.hollowengine.common.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider


class CommandBuilder<T : SharedSuggestionProvider>(private val dispatcher: CommandDispatcher<T>) {
    operator fun String.invoke(operation: CommandEditor<T>.() -> Unit) {
        val command = LiteralArgumentBuilder.literal<T>(this)
        CommandEditor<T>(command).operation()
        dispatcher.register(command)
    }
}

class CommandEditor<T : SharedSuggestionProvider>(private val srcCommand: LiteralArgumentBuilder<T>) {
    operator fun String.invoke(
        vararg args: RequiredArgumentBuilder<T, *>,
        operation: CommandContext<T>.() -> Unit,
    ) {
        if (args.isNotEmpty()) srcCommand.then(
            LiteralArgumentBuilder.literal<T>(this).then(operation, *args)
                .executes { ctx: CommandContext<T> -> operation(ctx); 1 })
        else srcCommand.then(
            LiteralArgumentBuilder.literal<T>(this).executes { ctx -> operation(ctx); 1 })
    }
}

fun <S, T : ArgumentBuilder<S, T>> ArgumentBuilder<S, T>.then(
    data: CommandContext<S>.() -> Unit,
    vararg argument: ArgumentBuilder<S, *>,
): T {
    return if (argument.size > 1) this.then(
        argument[0].then(
            data,
            *argument.copyOfRange(1, argument.size)
        )
    ) else this.then(
        argument[0].executes { data(it); 1 } as ArgumentBuilder<S, *>
    )
}

fun <T, V : SharedSuggestionProvider> arg(name: String, type: ArgumentType<T>): RequiredArgumentBuilder<V, T> =
    RequiredArgumentBuilder.argument(name, type)

@JvmName("argString")
fun <T, V : SharedSuggestionProvider> arg(
    name: String,
    type: ArgumentType<T>,
    suggests: () -> Collection<String>,
): RequiredArgumentBuilder<V, T> =
    RequiredArgumentBuilder.argument<V, T>(name, type).apply {
        suggests { _, builder: SuggestionsBuilder ->
            suggests().forEach(builder::suggest)
            builder.buildFuture()
        }
    }

@JvmName("argInt")
fun <T> arg(
    name: String,
    type: ArgumentType<T>,
    suggests: Collection<Int>,
): RequiredArgumentBuilder<CommandSourceStack, T> =
    Commands.argument(name, type).suggests { _, builder ->
        suggests.forEach(builder::suggest)
        builder.buildFuture()
    }

fun <T : SharedSuggestionProvider> CommandDispatcher<T>.onRegisterCommands(builder: CommandBuilder<T>.() -> Unit) {
    builder(CommandBuilder(this))
}