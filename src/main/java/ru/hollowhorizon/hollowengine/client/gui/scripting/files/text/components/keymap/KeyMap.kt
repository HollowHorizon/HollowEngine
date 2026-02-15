package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.keymap

import de.fabmax.kool.input.KeyEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandKey
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.CommandRegistry
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.components.EditorCommandContext

object KeyMap {
    private data class Entry(
        val binding: KeyBinding,
        val command: CommandKey,
        val priority: Int,
    )

    private val bindings = mutableListOf<Entry>()

    fun bind(
        binding: KeyBinding,
        command: CommandKey,
        priority: Int = 0,
    ) {
        bindings.add(Entry(binding, command, priority))
    }

    fun resolve(event: KeyEvent, ctx: EditorCommandContext): CommandKey? {
        var best: Entry? = null
        for (entry in bindings) {
            if (!entry.binding.matches(event)) continue
            if (!CommandRegistry.canExecute(entry.command, ctx)) continue
            if (best == null || entry.priority > best!!.priority) {
                best = entry
            }
        }
        return best?.command
    }
}
