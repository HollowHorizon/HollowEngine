package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.keys

import com.facebook.ktfmt.format.Formatter
import de.fabmax.kool.input.LocalKeyCode
import de.fabmax.kool.util.logW
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.ScriptTextEditorHandler
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.fullText

private val REFORMAT_KEY = LocalKeyCode('l')

@SubscribeEvent
fun onReformat(event: ScriptAreaKeyEvent) {
    if (event.localKeyCode != REFORMAT_KEY || !event.isReleased || !event.isCtrlDown || !event.isAltDown) return
    val editorHandler = event.area.modifier.editorHandler as? ScriptTextEditorHandler

    try {
        val original = event.area.lineProvider.fullText()
        val new = Formatter.format(original)
        if (original == new) return
        editorHandler?.replaceAll(new, event.area)
        event.area.modifier.onSelectionChanged?.let { it(-1, -1, 0, 0) }
        event.isCanceled = true
    } catch (ex: Exception) {
        event.area.logW { ex.stackTraceToString() }
    }
}