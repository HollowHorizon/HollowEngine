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
        CommandRegistry.register(CompletionAcceptCommand.Key, CompletionAcceptCommand())
        CommandRegistry.register(CompletionCancelCommand.Key, CompletionCancelCommand())
        CommandRegistry.register(CompletionNavigateUpCommand.Key, CompletionNavigateUpCommand())
        CommandRegistry.register(CompletionNavigateDownCommand.Key, CompletionNavigateDownCommand())
        CommandRegistry.register(ReformatCommand.Key, ReformatCommand())
        CommandRegistry.register(GoToDefinitionCommand.Key, GoToDefinitionCommand())
        CommandRegistry.register(IndentCommand.Key, IndentCommand())
        CommandRegistry.register(UnindentCommand.Key, UnindentCommand())
        CommandRegistry.register(ToggleLineCommentCommand.Key, ToggleLineCommentCommand())
        CommandRegistry.register(InsertNewlineCommand.Key, InsertNewlineCommand())
        CommandRegistry.register(ApplyBracketsCommand.Key, ApplyBracketsCommand())
        CommandRegistry.register(ApplyCompletionItemCommand.Key, ApplyCompletionItemCommand())
    }
}
