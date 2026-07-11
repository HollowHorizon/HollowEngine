package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.layout.invalidateLayout
import ru.hollowhorizon.hollowengine.client.ui.text.UiTextLayout

class SpanNode(
    text: String,
    id: String? = null,
    tags: Iterable<String> = emptyList(),
    modifiers: Iterable<Modifier> = emptyList(),
) : BaseUiNode(UiSpanType, id, tags, modifiers) {
    var text: String = text
        set(value) {
            if (field == value) return
            field = value
            invalidateLayout()
        }

    /**
     * The span's line fragments, computed by the parent inline flow during placement:
     * lines/runs are relative to the span's bounding rect, one run per word so justified
     * lines position each word exactly.
     */
    var lineLayout: UiTextLayout? = null
        internal set
}
