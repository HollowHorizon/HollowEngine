package ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.keys

import com.facebook.ktfmt.format.Formatter
import com.facebook.ktfmt.format.Formatter.KOTLINLANG_FORMAT
import de.fabmax.kool.input.UniversalKeyCode
import de.fabmax.kool.util.logW
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.fullText
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.CompiledFileProvider

private val REFORMAT_KEY = UniversalKeyCode('l')

@SubscribeEvent
fun onReformat(event: ScriptAreaKeyEvent) {
    if (event.keyCode != REFORMAT_KEY || !event.isReleased || !event.isCtrlDown || !event.isAltDown) return
    val editorHandler = event.area.modifier.editorHandler as? CompiledFileProvider ?: return

    try {
        val original = event.area.lineProvider.fullText()
        val new = Formatter.format(KOTLINLANG_FORMAT, original)
        if (original == new) return
        editorHandler.setText(new)
        event.isCanceled = true
    } catch (ex: Exception) {
        event.area.logW { ex.stackTraceToString() }
    }
}