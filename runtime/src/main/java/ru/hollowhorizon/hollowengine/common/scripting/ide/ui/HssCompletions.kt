package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import ru.hollowhorizon.hollowengine.client.ui.style.HssProperty
import ru.hollowhorizon.hollowengine.client.ui.style.HssSchema
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItemTag
import ru.hollowhorizon.hollowengine.common.scripting.ide.completionMatches
import ru.hollowhorizon.hollowengine.common.scripting.ide.declarationCompletionItem

/**
 * Completion for HSS. The scanner decides where the caret is, and the schema decides what
 * fits there: property names come with their full signature, and after the colon the
 * suggestions are those of the exact slot being typed.
 */
internal fun hssCompletions(text: String, offset: Int): List<CompletionItem> {
    val caret = offset.coerceIn(0, text.length)
    val before = text.substring(0, caret)
    val model = HssDocumentModel(text)
    val state = HssLexer(before, model.keyframeNames).scanState()

    return when (state.region) {
        HssRegion.VALUE -> valueCompletions(before, caret, state.property, state.valueStart, model)
        HssRegion.PROPERTY -> propertyCompletions(identifierPrefix(before))
        HssRegion.KEYFRAME_SELECTOR -> keywordCompletions(KeyframeSelectors, identifierPrefix(before), "offset")
        HssRegion.SELECTOR -> selectorCompletions(before, model)
    }
}

private val KeyframeSelectors = listOf("from", "to", "0%", "25%", "50%", "75%", "100%")

private fun propertyCompletions(prefix: String): List<CompletionItem> =
    HssSchema.properties
        .flatMap { property -> property.namesFor(prefix).map { property to it } }
        .map { (property, name) -> propertyItem(property, name) }

private fun HssProperty.namesFor(prefix: String): List<String> =
    (listOf(name) + aliases).filter { completionMatches(prefix, it) }

/** `margin` with its signature and summary, inserting `margin: ` ready for the value. */
private fun propertyItem(property: HssProperty, name: String): CompletionItem = declarationCompletionItem {
    show = name
    insert = "$name: "
    this.name = name
    tag = CompletionItemTag.PROPERTY
    fqName = null
    middle = ": ${property.syntax.signature()}"
    tail = property.summary
}

private fun valueCompletions(
    before: String,
    caret: Int,
    property: String?,
    valueStart: Int,
    model: HssDocumentModel,
): List<CompletionItem> {
    val declaration = property?.let(UiLanguageCatalog::property) ?: return emptyList()
    val value = before.substring(valueStart.coerceIn(0, before.length))
    val prefix = valuePrefix(before)
    val candidates = UiLanguageCatalog.valueCompletions(
        property = declaration.name,
        value = value,
        offset = caret - valueStart,
        keyframes = model.keyframeNames,
    )
    val slotName = declaration.slotAt(value, caret - valueStart)?.name ?: "value"
    return candidates
        .filter { completionMatches(prefix, it) }
        .map { candidate -> valueItem(candidate, slotName) }
}

private fun valueItem(candidate: String, slotName: String): CompletionItem = declarationCompletionItem {
    show = candidate
    insert = candidate
    name = candidate
    tag = CompletionItemTag.PROPERTY
    fqName = null
    middle = null
    tail = slotName
}

private fun selectorCompletions(before: String, model: HssDocumentModel): List<CompletionItem> {
    val prefix = identifierPrefix(before)
    val marker = before.getOrNull(before.length - prefix.length - 1)
    return when (marker) {
        '.' -> keywordCompletions(model.tags.toList(), prefix, "tag")
        '#' -> keywordCompletions(model.ids.toList(), prefix, "id")
        ':' -> keywordCompletions(UiLanguageCatalog.states, prefix, "state")
        else -> keywordCompletions(UiLanguageCatalog.elementTypes, prefix, "element")
    }
}

private fun keywordCompletions(values: List<String>, prefix: String, tail: String): List<CompletionItem> =
    values.filter { completionMatches(prefix, it) }.map { value ->
        declarationCompletionItem {
            show = value
            insert = value
            name = value
            tag = CompletionItemTag.KEYWORD
            fqName = null
            middle = null
            this.tail = tail
        }
    }

private fun identifierPrefix(before: String): String =
    before.takeLastWhile { it.isLetterOrDigit() || it == '-' || it == '_' }

private fun valuePrefix(before: String): String =
    before.takeLastWhile { it.isLetterOrDigit() || it in "-_#.%" }
