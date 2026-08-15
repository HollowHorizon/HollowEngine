package ru.hollowhorizon.hollowengine.common.scripting.ide

/**
 * Drains an analyzer's completion stream into a list. Production code consumes the batches as they
 * arrive; assertions want the whole set, so tests collect it here instead of the analyzers keeping
 * a second entry point alive for them.
 */
fun ScriptingAnalyzer.collectCompletions(name: String, text: String, offset: Int): List<CompletionItem> {
    val items = ArrayList<CompletionItem>()
    completions(name, text, offset) { batch ->
        items += batch
        true
    }
    return items
}
