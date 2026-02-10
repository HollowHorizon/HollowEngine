package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.keys

import de.fabmax.kool.input.KeyboardInput
import de.fabmax.kool.util.logD
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.ScriptTextAreaModifier
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent


@SubscribeEvent
fun onCompletions(event: ScriptAreaKeyEvent) {
    val modifier = event.area.modifier

    if (modifier.completions.isEmpty()) return

    when (event.keyCode) {
        KeyboardInput.KEY_TAB -> {
            if (!event.isReleased) return
            applyCompletion(modifier, event)
        }

        KeyboardInput.KEY_ENTER -> {
            if (!event.isPressed) return
            applyCompletion(modifier, event)
        }

        KeyboardInput.KEY_ESC -> {
            if (!event.isPressed) return
            modifier.completions.clear()
            modifier.completionIndex = 0
            event.isCanceled = true
        }

        KeyboardInput.KEY_CURSOR_UP -> {
            if (!event.isPressed) return
            if (modifier.completionIndex > 0) {
                event.area.completionsList.scrollToItem.set(modifier.completionIndex - 1)
                modifier.setCompletionIndex(modifier.completionIndex - 1)
            } else {
                modifier.setCompletionIndex(modifier.completions.lastIndex)
            }
            event.isCanceled = true
        }

        KeyboardInput.KEY_CURSOR_DOWN -> {
            if (!event.isPressed) return
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

private fun applyCompletion(
    modifier: ScriptTextAreaModifier,
    event: ScriptAreaKeyEvent,
) {
    val completionIndex = modifier.completionIndex
    
    if (completionIndex < 0 || completionIndex >= modifier.completions.size) {
        logD("Completions") { "Invalid completion index: $completionIndex" }
        return
    }
    
    val completionItem = modifier.completions[completionIndex]
    
    event.area.applyCompletion(completionItem)
    
    event.isCanceled = true
}