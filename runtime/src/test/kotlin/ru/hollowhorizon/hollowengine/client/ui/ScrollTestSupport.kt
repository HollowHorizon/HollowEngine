package ru.hollowhorizon.hollowengine.client.ui

import ru.hollowhorizon.hollowengine.client.ui.scroll.UiScrollHandle

internal fun scrollModifier(
    vertical: Boolean = true,
    horizontal: Boolean = true,
    verticalScrollbar: Boolean = true,
    horizontalScrollbar: Boolean = true,
    state: UiScrollHandle = UiScrollHandle(),
): ScrollModifier = ScrollModifier(
    UiScrollSpec(vertical, horizontal, verticalScrollbar, horizontalScrollbar, state)
)
