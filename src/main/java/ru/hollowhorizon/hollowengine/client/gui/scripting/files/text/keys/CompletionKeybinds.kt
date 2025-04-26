package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.keys

import de.fabmax.kool.input.KeyboardInput
import ru.hollowhorizon.hc.common.events.SubscribeEvent

private val APPLY_COMPLETION_KEYBIND = setOf(KeyboardInput.KEY_ENTER, KeyboardInput.KEY_TAB)


@SubscribeEvent
fun onCompletions(event: ScriptAreaKeyEvent) {
    val modifier = event.area.modifier

    if (modifier.completions.isEmpty()) return

    when (event.keyCode) {
        in APPLY_COMPLETION_KEYBIND -> {
            if(!event.isReleased) return
            if (modifier.completionIndex == -1) return
            if(modifier.completionIndex >= modifier.completions.size) return

            modifier.completions[modifier.completionIndex].use(event.area)
            event.isCanceled = true
        }

        KeyboardInput.KEY_ESC -> {
            if(!event.isPressed) return
            modifier.completions.clear()
            event.isCanceled = true
        }

        KeyboardInput.KEY_CURSOR_UP -> {
            if(!event.isPressed) return
            if (modifier.completionIndex > 0) {
                event.area.completionsList.scrollToItem.set(modifier.completionIndex - 1)
                modifier.setCompletionIndex(modifier.completionIndex - 1)
            } else {
                modifier.setCompletionIndex(modifier.completions.lastIndex)
            }
            event.isCanceled = true
        }

        KeyboardInput.KEY_CURSOR_DOWN -> {
            if(!event.isPressed) return
            if (modifier.completionIndex < modifier.completions.size - 1) {
                event.area.completionsList.scrollToItem.set(modifier.completionIndex + 1)
                modifier.setCompletionIndex(modifier.completionIndex + 1)
            } else {
                modifier.setCompletionIndex(0)
            }
            event.isCanceled = true
        }

        else -> return
    }
}