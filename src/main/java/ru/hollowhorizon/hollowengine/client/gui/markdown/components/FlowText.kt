package ru.hollowhorizon.hollowengine.client.gui.markdown.components

import de.fabmax.kool.modules.ui2.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.TextAttributes

fun UiScope.FlowText(spans: List<Pair<String, TextAttributes>>, scope: FlowScope.() -> Unit) {
    FlowRow(Grow.Std) {
        modifier.border(DebugBorder)
        spans.forEach { (span, attributes) ->
            val words = span.split(" ")
            words.forEachIndexed { i, text ->
                val space = if(words.lastIndex == i) "" else " "
                Text("$text$space") {
                    modifier.font(attributes.font)
                        .textColor(attributes.color)
                        .backgroundColor(attributes.background)
                }
            }
        }
        scope()
    }
}