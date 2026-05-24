package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.keymap

import de.fabmax.kool.input.KeyboardInput
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.commands.*
import de.fabmax.kool.input.UniversalKeyCode as key

object EditorDefaultKeys {
    private var registered = false

    fun ensureRegistered() {
        if (registered) return
        registered = true

        KeyMap.bind(KeyBinding(key('a'), ctrl = true), SelectAllCommand.Key)
        KeyMap.bind(KeyBinding(key('v'), ctrl = true), PasteCommand.Key)
        KeyMap.bind(KeyBinding(key('c'), ctrl = true), CopyCommand.Key)
        KeyMap.bind(KeyBinding(key('x'), ctrl = true), CutCommand.Key)
        KeyMap.bind(KeyBinding(key('z'), ctrl = true), UndoCommand.Key)
        KeyMap.bind(KeyBinding(key('z'), ctrl = true, shift = true), RedoCommand.Key)
        KeyMap.bind(KeyBinding(key('y'), ctrl = true), RedoCommand.Key)
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
}
