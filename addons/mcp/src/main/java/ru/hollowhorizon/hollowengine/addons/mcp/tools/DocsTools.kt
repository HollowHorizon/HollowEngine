package ru.hollowhorizon.hollowengine.addons.mcp.tools

import ru.hollowhorizon.hollowengine.addons.mcp.McpTool
import ru.hollowhorizon.hollowengine.addons.mcp.ParameterType
import ru.hollowhorizon.hollowengine.addons.mcp.ToolParameter
import ru.hollowhorizon.hollowengine.addons.mcp.docs.DocsLibrary
import ru.hollowhorizon.hollowengine.addons.mcp.docs.DocsPage
import ru.hollowhorizon.hollowengine.addons.mcp.errorResult
import ru.hollowhorizon.hollowengine.addons.mcp.textResult
import ru.hollowhorizon.hollowengine.addons.mcp.truncate

internal fun docsTools(docs: DocsLibrary): List<McpTool> = listOf(docsSearchTool(docs), docsReadTool(docs))

private fun docsSearchTool(docs: DocsLibrary) = McpTool(
    name = "docs_search",
    description = """
        Searches HollowEngine's guides (script types, node scripts, NPCs, dialogues, UI, models,
        animations, cutscenes, the IDE…) for sections holding every word of the query. Answers
        page#section ids to open with docs_read.
    """.trimIndent(),
    parameters = listOf(
        ToolParameter("query", ParameterType.STRING, "Words to look for", required = true),
        ToolParameter("limit", ParameterType.INTEGER, "Most sections to return, $DEFAULT_HITS by default"),
    ),
    readOnly = true,
) { arguments ->
    val query = arguments.string("query")
    val hits = docs.search(query, arguments.int("limit", DEFAULT_HITS).coerceIn(1, MAX_HITS))
    if (hits.isEmpty()) return@McpTool textResult("No section mentions every word of '$query'. Try fewer or other words, or docs_read without a page for the contents")
    textResult(hits.joinToString("\n") { hit ->
        val place = if (hit.section.anchor.isEmpty()) hit.page.id else "${hit.page.id}#${hit.section.anchor}"
        val heading = listOf(hit.page.title, hit.section.heading).filter(String::isNotBlank).distinct().joinToString(" › ")
        "$place — $heading: ${hit.excerpt.take(EXCERPT_LENGTH)}"
    })
}

private fun docsReadTool(docs: DocsLibrary) = McpTool(
    name = "docs_read",
    description = """
        Reads a HollowEngine guide page, or one section of it with its subsections. Without a page it
        lists every page. Links inside pages point at other page ids.
    """.trimIndent(),
    parameters = listOf(
        ToolParameter("page", ParameterType.STRING, "Page id such as 'scripting/node/states'; may carry '#section'"),
        ToolParameter("section", ParameterType.STRING, "Section anchor, as docs_search gives it after '#'"),
    ),
    readOnly = true,
) { arguments ->
    val requested = arguments.optionalString("page") ?: return@McpTool textResult(contents(docs))
    val page = docs.page(requested.substringBefore('#'))
        ?: return@McpTool errorResult("There is no page '$requested'. docs_read without a page lists them")
    val anchor = arguments.optionalString("section") ?: requested.substringAfter('#', "").takeIf(String::isNotEmpty)
    val text = if (anchor == null) page.text else page.section(anchor)
        ?: return@McpTool errorResult("'${page.id}' has no section '$anchor'. Its sections: ${page.sections.map { it.anchor }.filter(String::isNotEmpty).joinToString()}")
    textResult(truncate("# ${page.title} (${page.id})\n\n$text", limit = MAX_PAGE_CHARACTERS))
}

private fun contents(docs: DocsLibrary): String {
    if (docs.pages.isEmpty()) return "This build of the addon carries no guides"
    return docs.pages
        .sortedWith(compareBy<DocsPage> { it.id.substringBeforeLast('/', "") }.thenBy { it.order }.thenBy { it.id })
        .joinToString("\n") { page -> "${page.id} — ${page.title}" }
}

private const val DEFAULT_HITS = 10
private const val MAX_HITS = 50
private const val EXCERPT_LENGTH = 160
private const val MAX_PAGE_CHARACTERS = 60_000
