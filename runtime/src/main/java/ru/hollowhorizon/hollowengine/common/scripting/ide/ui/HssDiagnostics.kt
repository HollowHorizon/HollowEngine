package ru.hollowhorizon.hollowengine.common.scripting.ide.ui

import ru.hollowhorizon.hollowengine.client.ui.style.HssDeclaration
import ru.hollowhorizon.hollowengine.client.ui.style.HssSchema
import ru.hollowhorizon.hollowengine.client.ui.style.HssValueKind
import ru.hollowhorizon.hollowengine.client.ui.toStylePatch
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.Position
import ru.hollowhorizon.hollowengine.common.scripting.ide.Range
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity

/**
 * Diagnostics for a stylesheet: syntax errors reported on the token they are about, plus
 * the semantic checks the schema makes possible unknown properties, values the compiler
 * would reject, and animations naming a `@keyframes` block that does not exist.
 */
internal fun hssDiagnostics(text: String): List<Diagnostic> {
    val model = HssDocumentModel(text)
    val offsets = LineOffsets(text)
    val diagnostics = ArrayList<Diagnostic>()

    for (error in model.errors) {
        diagnostics += offsets.diagnostic(error.position, error.endPosition, Severity.ERROR, error.messageText)
    }
    for (declaration in model.declarations) {
        diagnostics += declarationDiagnostics(declaration, model, offsets)
    }
    return diagnostics.sortedWith(compareBy({ it.range.start.line }, { it.range.start.column }))
}

private fun declarationDiagnostics(
    declaration: HssDeclaration,
    model: HssDocumentModel,
    offsets: LineOffsets,
): List<Diagnostic> {
    val propertyRange = declaration.propertyRange ?: return emptyList()
    val property = HssSchema.find(declaration.property)
        ?: return listOf(
            offsets.diagnostic(
                propertyRange.first,
                propertyRange.last + 1,
                Severity.WARNING,
                unknownPropertyMessage(declaration.property),
            ),
        )

    val valueRange = declaration.valueRange ?: return emptyList()
    val diagnostics = ArrayList<Diagnostic>()
    try {
        // Most properties parse their value lazily inside the patch writer, so the value is
        // only really checked once the modifier is applied to a patch.
        property.compile(declaration.value.trim())?.let { listOf(it).toStylePatch() }
    } catch (exception: IllegalArgumentException) {
        diagnostics += offsets.diagnostic(
            valueRange.first,
            valueRange.last + 1,
            Severity.ERROR,
            exception.message ?: "Invalid value for '${property.name}'",
        )
    } catch (exception: NumberFormatException) {
        diagnostics += offsets.diagnostic(
            valueRange.first,
            valueRange.last + 1,
            Severity.ERROR,
            "Expected a number in '${declaration.value.trim()}'",
        )
    }
    diagnostics += keyframeDiagnostics(declaration, property.name, model, offsets)
    return diagnostics
}

/** Flags `animations: fade …` when no `@keyframes fade` exists in the document. */
private fun keyframeDiagnostics(
    declaration: HssDeclaration,
    property: String,
    model: HssDocumentModel,
    offsets: LineOffsets,
): List<Diagnostic> {
    if (property != "animations" && property != "animation-name") return emptyList()
    val schema = HssSchema.find(property) ?: return emptyList()
    val valueStart = declaration.valueStart
    if (valueStart < 0 || declaration.value.equals("none", ignoreCase = true)) return emptyList()
    val diagnostics = ArrayList<Diagnostic>()
    for (entry in schema.entriesOf(declaration.value)) {
        for ((index, word) in entry.words.withIndex()) {
            val slot = schema.syntax.entry.slotAt(entry.words.map { it.text }, index) ?: continue
            if (slot.kind != HssValueKind.KEYFRAMES) continue
            val name = word.text.trim('"', '\'')
            if (name.isEmpty() || name in model.keyframeNames) continue
            diagnostics += offsets.diagnostic(
                valueStart + word.start,
                valueStart + word.end,
                Severity.WARNING,
                "No '@keyframes $name' in this stylesheet",
            )
        }
    }
    return diagnostics
}

private fun unknownPropertyMessage(property: String): String {
    val suggestion = HssSchema.allNames.minByOrNull { editDistance(property.lowercase(), it) }
        ?.takeIf { editDistance(property.lowercase(), it) <= property.length / 2 + 1 }
    return if (suggestion == null) {
        "Unknown property '$property'"
    } else {
        "Unknown property '$property'; did you mean '$suggestion'?"
    }
}

/** Small Levenshtein distance, used only to suggest a property name on a typo. */
private fun editDistance(left: String, right: String): Int {
    if (left == right) return 0
    var previous = IntArray(right.length + 1) { it }
    var current = IntArray(right.length + 1)
    for (i in 1..left.length) {
        current[0] = i
        for (j in 1..right.length) {
            val substitution = previous[j - 1] + if (left[i - 1] == right[j - 1]) 0 else 1
            current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[right.length]
}

/** Offset-to-position mapping computed once per diagnostics pass. */
private class LineOffsets(text: String) {
    private val starts = buildList {
        add(0)
        text.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
    }

    fun diagnostic(start: Int, end: Int, severity: Severity, message: String): Diagnostic =
        Diagnostic(Range(position(start), position(maxOf(end, start + 1))), severity, message)

    private fun position(offset: Int): Position {
        val line = starts.binarySearch(offset).let { if (it >= 0) it else -it - 2 }.coerceAtLeast(0)
        return Position(line, offset - starts[line])
    }
}
