package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands

import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandRegistry

object EditorDefaultCommands {
    private var registered = false

    fun ensureRegistered() {
        if (registered) return
        registered = true

        CommandRegistry.register(SelectAllCommand.Key, SelectAllCommand())
        CommandRegistry.register(CopyCommand.Key, CopyCommand())
        CommandRegistry.register(CutCommand.Key, CutCommand())
        CommandRegistry.register(PasteCommand.Key, PasteCommand())
        CommandRegistry.register(UndoCommand.Key, UndoCommand())
        CommandRegistry.register(RedoCommand.Key, RedoCommand())
        CommandRegistry.register(ToggleLineCommentCommand.Key, ToggleLineCommentCommand())
    }
}
