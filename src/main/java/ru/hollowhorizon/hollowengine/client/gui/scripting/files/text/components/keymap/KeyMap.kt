package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.keymap

import de.fabmax.kool.input.KeyEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey

object KeyMap {
    private val bindings = LinkedHashMap<KeyBinding, CommandKey>()

    fun bind(binding: KeyBinding, command: CommandKey) {
        bindings[binding] = command
    }

    fun resolve(event: KeyEvent): CommandKey? {
        for ((binding, command) in bindings) {
            if (binding.matches(event)) return command
        }
        return null
    }
}
