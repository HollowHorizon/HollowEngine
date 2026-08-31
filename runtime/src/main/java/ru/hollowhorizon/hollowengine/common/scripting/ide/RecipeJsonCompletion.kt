package ru.hollowhorizon.hollowengine.common.scripting.ide

import net.minecraft.core.registries.BuiltInRegistries

private const val CompletionBatchSize = 128

internal fun completeRecipeItems(
    name: String,
    text: String,
    offset: Int,
    sink: CompletionSink,
) {
    val prefix = recipeItemValuePrefix(name, text, offset) ?: return
    val normalizedPrefix = prefix.lowercase()
    val filterByPath = ':' in prefix
    val batch = ArrayList<CompletionItem>(CompletionBatchSize)

    for (id in RecipeItemIds.values) {
        val path = id.substringAfter(':')
        val matches = normalizedPrefix.isEmpty() ||
                id.startsWith(normalizedPrefix) ||
                (!filterByPath && path.startsWith(normalizedPrefix))
        if (!matches) continue

        batch += declarationCompletionItem {
            show = id
            insert = id
            this.name = if (filterByPath) path else id
            tag = CompletionItemTag.PROPERTY
            fqName = null
            itemIcon = id
            wordChars = ":-/."
        }
        if (batch.size == CompletionBatchSize) {
            if (!sink.emit(batch.toList())) return
            batch.clear()
        }
    }
    if (batch.isNotEmpty()) sink.emit(batch)
}

/** Returns the resource-id prefix at the caret only for item-valued fields in recipe JSON files. */
internal fun recipeItemValuePrefix(name: String, text: String, offset: Int): String? {
    if (!name.isRecipeJsonPath()) return null
    val caret = offset.coerceIn(0, text.length)
    val valueStart = currentStringStart(text, caret) ?: return null
    val key = keyBefore(text, valueStart) ?: return null
    val itemValue = when (key) {
        "item", "result" -> true
        "id" -> enclosingObjectStart(text, valueStart)?.let { keyBefore(text, it) } == "result"
        else -> false
    }
    return if (itemValue) text.substring(valueStart + 1, caret) else null
}

private fun String.isRecipeJsonPath(): Boolean {
    val path = replace('\\', '/').lowercase()
    if (!path.endsWith(".json")) return false
    val rooted = "/${path.trimStart('/')}"
    return rooted.contains("/data/") &&
            (rooted.contains("/recipe/") || rooted.contains("/recipes/"))
}

private fun currentStringStart(text: String, end: Int): Int? {
    var stringStart = -1
    var escaped = false
    for (index in 0 until end) {
        val char = text[index]
        if (stringStart < 0) {
            if (char == '"') stringStart = index
            continue
        }
        when {
            escaped -> escaped = false
            char == '\\' -> escaped = true
            char == '"' -> stringStart = -1
        }
    }
    return stringStart.takeIf { it >= 0 }
}

private fun keyBefore(text: String, valueStart: Int): String? {
    var index = valueStart - 1
    while (index >= 0 && text[index].isWhitespace()) index--
    if (index < 0 || text[index] != ':') return null
    index--
    while (index >= 0 && text[index].isWhitespace()) index--
    if (index < 0 || text[index] != '"') return null
    val end = index
    index--
    while (index >= 0) {
        if (text[index] == '"' && !text.isEscaped(index)) return text.substring(index + 1, end)
        index--
    }
    return null
}

private fun enclosingObjectStart(text: String, end: Int): Int? {
    val containers = ArrayDeque<Pair<Char, Int>>()
    var inString = false
    var escaped = false
    for (index in 0 until end) {
        val char = text[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{', '[' -> containers.addLast(char to index)
            '}' -> if (containers.lastOrNull()?.first == '{') containers.removeLast()
            ']' -> if (containers.lastOrNull()?.first == '[') containers.removeLast()
        }
    }
    return containers.lastOrNull { it.first == '{' }?.second
}

private fun String.isEscaped(index: Int): Boolean {
    var slashes = 0
    var cursor = index - 1
    while (cursor >= 0 && this[cursor] == '\\') {
        slashes++
        cursor--
    }
    return slashes % 2 != 0
}

private object RecipeItemIds {
    val values: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        BuiltInRegistries.ITEM.keySet().map { it.toString() }.sorted()
    }
}
