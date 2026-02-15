package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components

interface CommandKey

interface Command {
    fun execute(c: EditorCommandContext): Boolean

    fun canExecute(c: EditorCommandContext): Boolean = true
}

object CommandRegistry {
    private val commands = LinkedHashMap<CommandKey, Command>()

    fun register(key: CommandKey, command: Command) {
        commands[key] = command
    }

    fun execute(key: CommandKey, ctx: EditorCommandContext): Boolean {
        return commands[key]?.execute(ctx) ?: false
    }

    fun canExecute(key: CommandKey, ctx: EditorCommandContext): Boolean {
        return commands[key]?.canExecute(ctx) ?: false
    }
}
