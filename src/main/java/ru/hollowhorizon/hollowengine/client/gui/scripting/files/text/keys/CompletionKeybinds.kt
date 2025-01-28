package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.keys

import de.fabmax.kool.input.KeyboardInput
import ru.hollowhorizon.hc.common.events.SubscribeEvent

private val APPLY_COMPLETION_KEYBIND = setOf(KeyboardInput.KEY_ENTER, KeyboardInput.KEY_TAB)


@SubscribeEvent
fun onCompletions(event: ScriptAreaKeyEvent) {
    val modifier = event.area.modifier

    if (modifier.completions.isEmpty() || !event.isPressed) return

    when (event.keyCode) {
        in APPLY_COMPLETION_KEYBIND -> {
            if (modifier.completionIndex == -1) return

            modifier.completions[modifier.completionIndex].use(event.area)
        }

        KeyboardInput.KEY_ESC -> modifier.completions.clear()

        KeyboardInput.KEY_CURSOR_UP -> {
            if (modifier.completionIndex > 0) {
                event.area.completionsList.scrollToItem.set(modifier.completionIndex - 1)
                modifier.setCompletionIndex(modifier.completionIndex - 1)
            }
        }

        KeyboardInput.KEY_CURSOR_DOWN -> {
            if (modifier.completionIndex < modifier.completions.size - 1) {
                event.area.completionsList.scrollToItem.set(modifier.completionIndex + 1)
                modifier.setCompletionIndex(modifier.completionIndex + 1)
            }
        }

        else -> return
    }

    event.isCanceled = true
}