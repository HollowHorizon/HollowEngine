package ru.hollowhorizon.hollowengine.client.ui.style

import ru.hollowhorizon.hollowengine.client.ui.UiBorder
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiCheckboxStyle
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiCheckboxVariant
import ru.hollowhorizon.hollowengine.client.ui.widgets.UiSliderStyle

private fun partFitSyntax() = syntax(
    keywordSlot("fit", *UiImageFitKeywords.toTypedArray()),
    sizeSlot("slice", auto = false).copy(optional = true),
)

private fun borderSyntax() = syntax(sizeSlot("width", auto = false), slot("color", HssValueKind.COLOR))

/** Styling of the built-in widgets: scrollbars, sliders and checkboxes. */
internal fun widgetHssProperties(): List<HssProperty> = hssProperties {
    property(
        "scrollbar", "scrollbar-width", "scrollbar-thickness",
        summary = "Thickness of the scrollbar.",
        syntax = syntax(sizeSlot("thickness", auto = false)),
        examples = listOf("7px", "10px", "12px"),
    ) { style { it.scrollbar = (it.scrollbar ?: UiScrollbarStyle()).copy(thickness = parseLength(value)) } }

    property(
        "scrollbar-margin",
        summary = "Gap between the scrollbar and the content edge.",
        syntax = syntax(sizeSlot("margin", auto = false)),
        examples = listOf("2px", "3px", "4px"),
    ) { style { it.scrollbar = (it.scrollbar ?: UiScrollbarStyle()).copy(margin = parseLength(value)) } }

    property(
        "scrollbar-min-thumb", "scrollbar-min-thumb-size",
        summary = "Smallest size the scrollbar thumb shrinks to.",
        syntax = syntax(sizeSlot("size", auto = false)),
        examples = listOf("16px", "24px"),
    ) { style { it.scrollbar = (it.scrollbar ?: UiScrollbarStyle()).copy(minThumbSize = parseLength(value)) } }

    property(
        "scrollbar-overlay",
        summary = "Whether the scrollbar floats over the content instead of reserving space.",
        syntax = syntax(slot("overlay", HssValueKind.BOOLEAN)),
    ) { style { it.scrollbar = (it.scrollbar ?: UiScrollbarStyle()).copy(overlay = parseBoolean(value)) } }

    property(
        "scrollbar-track",
        summary = "Fill of the scrollbar track.",
        syntax = syntax(slot("paint", HssValueKind.PAINT)),
    ) { style { patch -> patch.scrollbar = patch.scrollbarStyle().patchTrack { it.copy(paint = parsePaint(value)) } } }

    property(
        "scrollbar-thumb",
        summary = "Fill of the scrollbar thumb.",
        syntax = syntax(slot("paint", HssValueKind.PAINT)),
    ) { style { patch -> patch.scrollbar = patch.scrollbarStyle().patchThumb { it.copy(paint = parsePaint(value)) } } }

    property(
        "scrollbar-track-border",
        summary = "Border of the scrollbar track.",
        syntax = borderSyntax(),
    ) {
        style { patch ->
            patch.scrollbar = patch.scrollbarStyle().patchTrack { it.copy(border = parseBorder(value, it.border ?: UiBorder())) }
        }
    }

    property(
        "scrollbar-thumb-border",
        summary = "Border of the scrollbar thumb.",
        syntax = borderSyntax(),
    ) {
        style { patch ->
            patch.scrollbar = patch.scrollbarStyle().patchThumb { it.copy(border = parseBorder(value, it.border ?: UiBorder())) }
        }
    }

    property(
        "scrollbar-track-radius",
        summary = "Corner radius of the scrollbar track.",
        syntax = syntax(slot("radius", HssValueKind.PIXELS)),
    ) { style { patch -> patch.scrollbar = patch.scrollbarStyle().patchTrack { it.copy(radius = parseScalar(value)) } } }

    property(
        "scrollbar-thumb-radius",
        summary = "Corner radius of the scrollbar thumb.",
        syntax = syntax(slot("radius", HssValueKind.PIXELS)),
    ) { style { patch -> patch.scrollbar = patch.scrollbarStyle().patchThumb { it.copy(radius = parseScalar(value)) } } }

    property(
        "scrollbar-track-fit",
        summary = "How the track image fills the track.",
        syntax = partFitSyntax(),
    ) {
        style { patch ->
            patch.scrollbar = patch.scrollbarStyle().patchTrack {
                it.copy(fit = parseImageFit(value), slice = parseImageFitSlice(value) ?: it.slice)
            }
        }
    }

    property(
        "scrollbar-thumb-fit",
        summary = "How the thumb image fills the thumb.",
        syntax = partFitSyntax(),
    ) {
        style { patch ->
            patch.scrollbar = patch.scrollbarStyle().patchThumb {
                it.copy(fit = parseImageFit(value), slice = parseImageFitSlice(value) ?: it.slice)
            }
        }
    }

    property(
        "scrollbar-track-slice",
        summary = "Nine-slice insets of the track image.",
        syntax = edgesSyntax(auto = false),
    ) {
        style { patch ->
            patch.scrollbar = patch.scrollbarStyle().patchTrack { it.copy(slice = parseInsets(value, allowAuto = false)) }
        }
    }

    property(
        "scrollbar-thumb-slice",
        summary = "Nine-slice insets of the thumb image.",
        syntax = edgesSyntax(auto = false),
    ) {
        style { patch ->
            patch.scrollbar = patch.scrollbarStyle().patchThumb { it.copy(slice = parseInsets(value, allowAuto = false)) }
        }
    }

    property(
        "slider-track-thickness", "slider-track-width",
        summary = "Thickness of the slider track.",
        syntax = syntax(sizeSlot("thickness", auto = false)),
    ) { style { it.slider = (it.slider ?: UiSliderStyle()).copy(trackThickness = parseLength(value)) } }

    property(
        "slider-track",
        summary = "Fill of the slider track.",
        syntax = syntax(slot("paint", HssValueKind.PAINT)),
    ) { style { it.slider = (it.slider ?: UiSliderStyle()).copy(trackPaint = parsePaint(value)) } }

    property(
        "slider-active-track", "slider-fill",
        summary = "Fill of the filled part of the slider track.",
        syntax = syntax(slot("paint", HssValueKind.PAINT)),
    ) { style { it.slider = (it.slider ?: UiSliderStyle()).copy(activeTrackPaint = parsePaint(value)) } }

    property(
        "slider-thumb",
        summary = "Fill of the slider thumb.",
        syntax = syntax(slot("paint", HssValueKind.PAINT)),
    ) { style { it.slider = (it.slider ?: UiSliderStyle()).copy(thumbPaint = parsePaint(value)) } }

    property(
        "slider-thumb-border",
        summary = "Border of the slider thumb.",
        syntax = borderSyntax(),
    ) {
        style {
            val current = it.slider ?: UiSliderStyle()
            it.slider = current.copy(thumbBorder = parseBorder(value, current.thumbBorder ?: UiBorder()))
        }
    }

    property(
        "slider-thumb-size",
        summary = "Size of the slider thumb; one value sizes both axes.",
        syntax = axesSyntax(sizeSlot("width", auto = false), sizeSlot("height", auto = false)),
        examples = listOf("10px 10px", "12px 20px"),
    ) { style { it.slider = (it.slider ?: UiSliderStyle()).copy(thumbSize = parseSize(value)) } }

    property(
        "slider-radius",
        summary = "Corner radius of the slider track and thumb.",
        syntax = syntax(slot("radius", HssValueKind.PIXELS)),
    ) { style { it.slider = (it.slider ?: UiSliderStyle()).copy(radius = parseScalar(value)) } }

    property(
        "checkbox-mark",
        summary = "Colour of the check mark.",
        syntax = syntax(slot("paint", HssValueKind.PAINT)),
    ) { style { it.checkbox = (it.checkbox ?: UiCheckboxStyle()).copy(markPaint = parsePaint(value)) } }

    property(
        "checkbox-active", "checkbox-fill",
        summary = "Fill of the box while checked.",
        syntax = syntax(slot("paint", HssValueKind.PAINT)),
    ) { style { it.checkbox = (it.checkbox ?: UiCheckboxStyle()).copy(activePaint = parsePaint(value)) } }

    property(
        "checkbox-variant", "checkbox-style",
        summary = "Shape the checkbox takes.",
        syntax = syntax(keywordSlot("variant", *enumKeywords<UiCheckboxVariant>().toTypedArray())),
        examples = enumKeywords<UiCheckboxVariant>(),
    ) { style { it.checkbox = (it.checkbox ?: UiCheckboxStyle()).copy(variant = UiCheckboxVariant.from(value)) } }
}

private fun UiStylePatch.scrollbarStyle(): UiScrollbarStyle = scrollbar ?: UiScrollbarStyle()
