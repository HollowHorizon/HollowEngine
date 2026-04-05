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
import net.minecraft.network.chat.Component

@DslMarker
annotation class CommandsDSL

@CommandsDSL
class CommandBuilder<S : SharedSuggestionProvider>(private val dispatcher: CommandDispatcher<S>) {
    operator fun String.invoke(vararg aliases: String, operation: CommandEditor<S, LiteralArgumentBuilder<S>>.() -> Unit) {
        val command = LiteralArgumentBuilder.literal<S>(this)
        CommandEditor<S, LiteralArgumentBuilder<S>>(command).operation()
        dispatcher.register(command)

        for (alias in aliases) {
            val aliasNode = LiteralArgumentBuilder.literal<S>(alias)
            aliasNode.redirect(command.build())
            dispatcher.register(aliasNode)
        }
    }
}

@CommandsDSL
class CommandEditor<S, T: ArgumentBuilder<S, T>>(private val srcCommand: ArgumentBuilder<S, T>) {
    fun requires(predicate: S.() -> Boolean) {
        srcCommand.requires(predicate)
    }

    fun executes(operation: CommandContext<S>.() -> Int) {
        srcCommand.executes(operation)
    }

    operator fun String.invoke(
        vararg args: RequiredArgumentBuilder<S, *>,
        operation: CommandEditor<S, T>.() -> Unit,
    ) {
        val literal = LiteralArgumentBuilder.literal<S>(this) as ArgumentBuilder<S, T>

        if (args.isNotEmpty()) {
            args.lastOrNull()?.let { CommandEditor<S, T>(it as ArgumentBuilder<S, T>).operation() }
            val chain = args.reduceRight { current, acc ->
                current.then(acc)
            }
            literal.then(chain)
        } else {
            val editor = CommandEditor(literal)
            editor.operation()
        }

        srcCommand.then(literal)
    }

    val SUCCESS = 1
    val FAILURE = 0
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

fun CommandContext<CommandSourceStack>.sendSuccess(allowLogging: Boolean = true, msg: () -> Component): Int {
    source.sendSuccess(msg, allowLogging)
    return 1
}

fun CommandContext<CommandSourceStack>.sendFailure(msg: Component): Int {
    source.sendFailure(msg)
    return 0
}