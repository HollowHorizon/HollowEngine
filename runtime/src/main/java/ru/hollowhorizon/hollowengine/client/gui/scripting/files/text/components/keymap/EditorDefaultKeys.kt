package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.keymap

import de.fabmax.kool.input.KeyboardInput
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.ApplyCompletionItemCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.CompletionAcceptCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.CompletionCancelCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.CompletionNavigateDownCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.CompletionNavigateUpCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.CopyCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.CutCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.GoToDefinitionCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.IndentCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.PasteCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.RedoCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.ReformatCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.SelectAllCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.ToggleLineCommentCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.UndoCommand
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.UnindentCommand
import de.fabmax.kool.input.UniversalKeyCode as key

object EditorDefaultKeys {
    private var registered = false

    fun ensureRegistered() {
        if (registered) return
        registered = true

        bindShortcut('A', SelectAllCommand.Key)
        bindShortcut('V', PasteCommand.Key)
        bindShortcut('C', CopyCommand.Key)
        bindShortcut('X', CutCommand.Key)
        bindShortcut('Z', UndoCommand.Key)
        bindShortcut('Z', RedoCommand.Key, shift = true)
        bindShortcut('Y', RedoCommand.Key)
        KeyMap.bind(KeyBinding(key('/'), ctrl = true), ToggleLineCommentCommand.Key)

        KeyMap.bind(
            KeyBinding(key('l'), ctrl = true, alt = true, trigger = KeyBinding.Trigger.Released),
            ReformatCommand.Key
        )
        KeyMap.bind(
            KeyBinding(KeyboardInput.KEY_F4, trigger = KeyBinding.Trigger.Released),
            GoToDefinitionCommand.Key
        )

        KeyMap.bind(
            KeyBinding(KeyboardInput.KEY_CURSOR_UP, trigger = KeyBinding.Trigger.Pressed),
            CompletionNavigateUpCommand.Key,
            priority = 50
        )
        KeyMap.bind(
            KeyBinding(KeyboardInput.KEY_CURSOR_DOWN, trigger = KeyBinding.Trigger.Pressed),
            CompletionNavigateDownCommand.Key,
            priority = 50
        )
        KeyMap.bind(
            KeyBinding(KeyboardInput.KEY_ENTER, trigger = KeyBinding.Trigger.Pressed),
            CompletionAcceptCommand.Key,
            priority = 50
        )
        KeyMap.bind(
            KeyBinding(KeyboardInput.KEY_NP_ENTER, trigger = KeyBinding.Trigger.Pressed),
            CompletionAcceptCommand.Key,
            priority = 50
        )
        KeyMap.bind(
            KeyBinding(KeyboardInput.KEY_ESC, trigger = KeyBinding.Trigger.Pressed),
            CompletionCancelCommand.Key,
            priority = 50
        )

        KeyMap.bind(
            KeyBinding(KeyboardInput.KEY_TAB, trigger = KeyBinding.Trigger.Released),
            ApplyCompletionItemCommand.Key,
            priority = 100
        )
        KeyMap.bind(
            KeyBinding(KeyboardInput.KEY_TAB, shift = true, trigger = KeyBinding.Trigger.Released),
            UnindentCommand.Key,
            priority = 10
        )
        KeyMap.bind(
            KeyBinding(KeyboardInput.KEY_TAB, trigger = KeyBinding.Trigger.Released),
            IndentCommand.Key,
            priority = 0
        )
    }

    private fun bindShortcut(char: Char, command: CommandKey, shift: Boolean = false) {
        KeyMap.bind(KeyBinding(key(char.uppercaseChar()), ctrl = true, shift = shift), command)
        KeyMap.bind(KeyBinding(key(char.lowercaseChar()), ctrl = true, shift = shift), command)
    }
}
