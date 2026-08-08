package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import ru.hollowhorizon.hollowengine.client.ui.style.HssDeclaration
import ru.hollowhorizon.hollowengine.client.ui.style.HssParseException
import ru.hollowhorizon.hollowengine.client.ui.style.HssParseResult
import ru.hollowhorizon.hollowengine.client.ui.style.HssSelector
import ru.hollowhorizon.hollowengine.client.ui.style.parseHssRecovering

/**
 * One parse of the edited stylesheet, shared by highlighting, hints and diagnostics.
 * Parsing recovers from broken rules, so a half-typed declaration never blanks out the
 * rest of the file.
 */
internal class HssDocumentModel(val text: String) {
    private val result: HssParseResult = parseHssRecovering(text)

    val errors: List<HssParseException> get() = result.errors

    val keyframeNames: Set<String> = result.document.keyframes.mapTo(LinkedHashSet()) { it.name }

    /** Every declaration in the document, rules and keyframes alike, in source order. */
    val declarations: List<HssDeclaration> = buildList {
        result.document.rules.forEach { addAll(it.declarations) }
        result.document.keyframes.forEach { keyframes ->
            keyframes.frames.forEach { addAll(it.declarations) }
        }
    }.sortedBy { it.propertyStart }

    /** Tag names used by the document's selectors, offered after `.` in completion. */
    val tags: Set<String> = result.document.rules
        .flatMapTo(LinkedHashSet()) { rule -> rule.selectors.flatMap { it.selectorTags() } }

    /** Ids used by the document's selectors, offered after `#` in completion. */
    val ids: Set<String> = result.document.rules
        .flatMapTo(LinkedHashSet()) { rule -> rule.selectors.mapNotNull { it.selectorId() } }

    /** Offset of the `@keyframes` block called [name], for go-to-definition. */
    fun keyframesNamed(name: String): Int? =
        result.document.keyframes.firstOrNull { it.name == name && it.nameStart >= 0 }?.nameStart
}

private fun HssSelector.selectorTags(): List<String> = tags.toList() + (ancestor?.selectorTags() ?: emptyList())

private fun HssSelector.selectorId(): String? = id ?: ancestor?.selectorId()
