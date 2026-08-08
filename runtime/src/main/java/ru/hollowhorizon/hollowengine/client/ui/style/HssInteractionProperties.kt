package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.ScrollAxes
import ru.hollowhorizon.hollowengine.client.ui.UiCursorShape

private fun booleanSyntax(name: String) = syntax(slot(name, HssValueKind.BOOLEAN))

/**
 * Input capabilities and scrolling. Each capability is its own property so a state rule
 * can toggle one without touching the rest (`.btn:disabled { clickable: false }`).
 */
internal fun interactionHssProperties(): List<HssProperty> = hssProperties {
    property(
        "hoverable",
        summary = "Whether the node reacts to the pointer entering it.",
        syntax = booleanSyntax("hoverable"),
    ) { set(UiProps.Hoverable, parseBoolean(value)) }

    property(
        "clickable",
        summary = "Whether the node receives click events.",
        syntax = booleanSyntax("clickable"),
    ) { set(UiProps.Clickable, parseBoolean(value)) }

    property(
        "focusable",
        summary = "Whether the node can take keyboard focus.",
        syntax = booleanSyntax("focusable"),
    ) { set(UiProps.Focusable, parseBoolean(value)) }

    property(
        "draggable",
        summary = "Whether the node emits drag events.",
        syntax = booleanSyntax("draggable"),
    ) { set(UiProps.Draggable, parseBoolean(value)) }

    property(
        "input-transparent",
        summary = "Whether pointer events pass straight through to whatever is behind.",
        syntax = booleanSyntax("transparent"),
    ) { set(UiProps.InputTransparent, parseBoolean(value)) }

    property(
        "scroll",
        summary = "Axes the node scrolls along.",
        syntax = syntax(keywordSlot("axes", "vertical", "horizontal", "both", "none")),
        examples = listOf("vertical", "horizontal", "both", "none"),
    ) { set(UiProps.Scroll, parseScrollAxes(value)) }

    property(
        "scrollable",
        summary = "Legacy switch for `scroll: both`; prefer `scroll`.",
        syntax = booleanSyntax("scrollable"),
    ) { set(UiProps.Scroll, if (parseBoolean(value)) ScrollAxes.Both else null) }

    property(
        "cursor",
        summary = "Pointer shape while hovering the node; inherited by children.",
        syntax = syntax(keywordSlot("cursor", *enumKeywords<UiCursorShape>().toTypedArray())),
        examples = enumKeywords<UiCursorShape>(),
    ) { set(UiProps.Cursor, parseCursor(value)) }
}
