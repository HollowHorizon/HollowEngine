package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

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
    }
    return hints
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
