package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import ru.hollowhorizon.hollowengine.client.ui.style.parseColor
import ru.hollowhorizon.hollowengine.common.scripting.ide.InlayAction
import ru.hollowhorizon.hollowengine.common.scripting.ide.InlayContent
import ru.hollowhorizon.hollowengine.common.scripting.ide.InlayHint
import ru.hollowhorizon.hollowengine.common.scripting.ide.InlayIcons
import ru.hollowhorizon.hollowengine.common.scripting.ide.InlayTags
import ru.hollowhorizon.hollowengine.common.scripting.ide.ResourceLocationTargets

private val LocationPattern = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")

/**
 * Names the tokens of a declaration value, so `margin: 8px 8px 60px 8px` reads as
 * `top=8px right=8px bottom=60px left=8px` right in the editor.
 *
 * Resource locations get a button instead of a label, opening the file they point at.
 */
internal fun hssInlayHints(model: HssDocumentModel): List<InlayHint> {
    val hints = ArrayList<InlayHint>()
    for (declaration in model.declarations) {
        val valueStart = declaration.valueStart
        if (valueStart < 0) continue
        val property = UiLanguageCatalog.property(declaration.property) ?: continue
        for (hint in property.hints(declaration.value)) {
            hints += InlayHint(valueStart + hint.offset, "${hint.label}=", tags = listOf(InlayTags.PARAMETER))
        }
        hints += locationHints(declaration.value, valueStart)
        hints += colorHints(declaration.value, valueStart)
    }
    return hints
}

/**
 * Every color literal in a declaration value gets a clickable chip in front of it, so a stylesheet
 * shows the colors it actually paints and any of them can be re-picked without hand-writing hex.
 */
private fun colorHints(value: String, valueStart: Int): List<InlayHint> =
    hssColorLiterals(value).map { literal ->
        InlayHint(
            index = valueStart + literal.start,
            content = listOf(InlayContent.Swatch(literal.argb)),
            tags = listOf(InlayTags.ACTION, InlayTags.COLOR),
            action = InlayAction.PickColor(
                start = valueStart + literal.start,
                end = valueStart + literal.end,
                literal = literal.text,
            ),
        )
    }

/** A color literal found inside a value, with offsets relative to the value's own start. */
internal data class HssColorLiteral(val start: Int, val end: Int, val text: String, val argb: Int)

private val ColorLiteralRegex = Regex(
    """#[0-9a-fA-F]{6,8}\b|\brgba?\s*\([^)]*\)|\b(?:transparent|white|black)\b""",
)

internal fun hssColorLiterals(value: String): List<HssColorLiteral> =
    ColorLiteralRegex.findAll(value).mapNotNull { match ->
        val color = runCatching { parseColor(match.value) }.getOrNull() ?: return@mapNotNull null
        HssColorLiteral(match.range.first, match.range.last + 1, match.value, color.toArgb())
    }.toList()

/** Renders a picked colour back into a value the stylesheet parses: `#RRGGBB`, or `#RRGGBBAA`. */
internal fun hssColorLiteralText(argb: Int): String {
    val alpha = argb ushr 24 and 0xFF
    val rgb = "%06X".format(argb and 0xFFFFFF)
    return if (alpha == 0xFF) "#$rgb" else "#$rgb" + "%02X".format(alpha)
}

/** Open buttons for every resource location inside a declaration value. */
private fun locationHints(value: String, valueStart: Int): List<InlayHint> =
    LocationPattern.findAll(value).mapNotNull { match ->
        val location = match.value
        val target = ResourceLocationTargets.targetOf(location) ?: return@mapNotNull null
        InlayHint(
            index = valueStart + match.range.first,
            content = listOf(InlayContent.Icon(InlayIcons.OPEN_RESOURCE)),
            tags = listOf(InlayTags.ACTION, target.tag),
            action = InlayAction.OpenResource(location),
        )
    }.toList()

/** The resource location the caret sits on, if any; used for go-to-definition. */
internal fun hssLocationAt(text: String, offset: Int): String? =
    LocationPattern.findAll(text).firstOrNull { offset in it.range.first..it.range.last + 1 }?.value
