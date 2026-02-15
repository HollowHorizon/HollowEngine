package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

interface CommandKey

fun interface Command {
    fun execute(c: EditorCommandContext): Boolean
}

object CommandRegistry {
    private val commands = LinkedHashMap<CommandKey, Command>()

    fun register(key: CommandKey, command: Command) {
        commands[key] = command
    }

    fun execute(key: CommandKey, ctx: EditorCommandContext): Boolean {
        return commands[key]?.execute(ctx) ?: false
    }
}
