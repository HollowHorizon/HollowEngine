package ru.hollowhorizon.hollowengine.addons.mcp.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.hollowhorizon.hollowengine.addons.mcp.COMPILER_MISSING
import ru.hollowhorizon.hollowengine.addons.mcp.McpTool
import ru.hollowhorizon.hollowengine.addons.mcp.ParameterType
import ru.hollowhorizon.hollowengine.addons.mcp.ToolInputException
import ru.hollowhorizon.hollowengine.addons.mcp.ToolParameter
import ru.hollowhorizon.hollowengine.addons.mcp.errorResult
import ru.hollowhorizon.hollowengine.addons.mcp.textResult
import ru.hollowhorizon.hollowengine.addons.mcp.truncate
import ru.hollowhorizon.hollowengine.common.scripting.ScriptingEnvironment
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity
import ru.hollowhorizon.hollowengine.common.scripting.source.ScriptRegistry
import java.io.File

internal fun codeTools(): List<McpTool> = listOf(scriptDiagnosticsTool(), symbolSearchTool(), symbolSourceTool())

private fun scriptDiagnosticsTool() = McpTool(
    name = "script_diagnostics",
    description = """
        Errors and warnings the in-game Kotlin analyzer reports for a script, against the classpath the
        game really compiles it with (Minecraft in Mojang names, loaded mods, addons). It compiles and
        runs nothing, so it is the quick check after editing a script. Pass `text` to check a version
        that is not saved yet. Positions are 1-based line:column.
    """.trimIndent(),
    parameters = listOf(
        ToolParameter(
            "path", ParameterType.STRING,
            "Script path as commands spell it: 'scripts/folder/name.node.kts' for the project's own scripts " +
                "(the hollowengine/scripts folder), 'namespace:path' for an addon's",
            required = true,
        ),
        ToolParameter("text", ParameterType.STRING, "Script source to check instead of the saved file"),
    ),
    readOnly = true,
) { arguments ->
    val analyzer = analyzerOrNull() ?: return@McpTool errorResult(COMPILER_MISSING)
    val path = arguments.string("path")
    if (!path.endsWith(".kts")) throw ToolInputException("Only Kotlin scripts (.kts) are analyzed")
    val display = ScriptRegistry.display(ScriptRegistry.parse(path))
    val text = arguments.optionalString("text") ?: readScript(path)
    val diagnostics = withContext(Dispatchers.IO) { analyzer.diagnostic(display, text) }
        .filter { it.severity >= Severity.WARNING }
    textResult(truncate(describe(display, text, diagnostics)))
}

private fun symbolSearchTool() = McpTool(
    name = "symbol_search",
    description = """
        Finds classes and Kotlin top-level functions and properties (extension functions included) on the
        script classpath by name: Minecraft in Mojang names, the engine's scripting API, mods and addons.
        The query is a simple name or part of one, optionally qualified by part of its package
        ('entity.Entity', 'npcs.spawn'). Closest matches come first. Read one with symbol_source.
    """.trimIndent(),
    parameters = listOf(
        ToolParameter("query", ParameterType.STRING, "Name or part of a name", required = true),
        ToolParameter("limit", ParameterType.INTEGER, "Most results to return, $DEFAULT_SEARCH_LIMIT by default"),
    ),
    readOnly = true,
) { arguments ->
    val analyzer = analyzerOrNull() ?: return@McpTool errorResult(COMPILER_MISSING)
    val query = arguments.string("query")
    val limit = arguments.int("limit", DEFAULT_SEARCH_LIMIT).coerceIn(1, MAX_SEARCH_LIMIT)
    val matches = withContext(Dispatchers.IO) { analyzer.searchSymbols(query, limit) }
    if (matches.isEmpty()) return@McpTool textResult("Nothing on the script classpath matches '$query'")
    textResult(matches.joinToString("\n") { match ->
        val kind = match.kind.name.lowercase()
        if (match.signature.isEmpty()) "$kind ${match.qualifiedName}" else "$kind ${match.qualifiedName}: ${match.signature}"
    })
}

private fun symbolSourceTool() = McpTool(
    name = "symbol_source",
    description = """
        Source of a class, member or top-level declaration by qualified name, from the library's sources jar
        when there is one and decompiled otherwise (Minecraft decompiles in Mojang names). Nested classes
        are spelled Outer.Inner, members Class.member; overloads show the first one. Long files come a
        window at a time: by default the lines around the declaration, and `line`/`lines` page further.
    """.trimIndent(),
    parameters = listOf(
        ToolParameter("name", ParameterType.STRING, "Qualified name, e.g. net.minecraft.world.entity.Entity.tick", required = true),
        ToolParameter("line", ParameterType.INTEGER, "First line to show, 1-based"),
        ToolParameter("lines", ParameterType.INTEGER, "How many lines to show, $DEFAULT_SOURCE_LINES by default"),
    ),
    readOnly = true,
) { arguments ->
    val analyzer = analyzerOrNull() ?: return@McpTool errorResult(COMPILER_MISSING)
    val name = arguments.string("name")
    val location = withContext(Dispatchers.IO) { analyzer.symbolSource(name) }
        ?: return@McpTool errorResult(
            "Nothing on the script classpath is named '$name'. Find the exact name with symbol_search; " +
                "nested classes are spelled Outer.Inner"
        )
    val text = location.text
        ?: return@McpTool textResult("'$name' is declared in the project's own file ${location.path}; read it there")

    val lines = text.lines()
    val declarationLine = text.substring(0, location.offset.coerceIn(0, text.length)).count { it == '\n' } + 1
    val count = arguments.int("lines", DEFAULT_SOURCE_LINES).coerceIn(1, MAX_SOURCE_LINES)
    val first = arguments.int("line", (declarationLine - LINES_BEFORE_DECLARATION).coerceAtLeast(1)).coerceIn(1, lines.size)
    val last = (first + count - 1).coerceAtMost(lines.size)
    val origin = if (location.path.startsWith("decompiled/")) "decompiled" else "source"
    val body = (first..last).joinToString("\n") { number -> "%6d  %s".format(number, lines[number - 1]) }
    textResult(
        truncate(
            "// ${location.path} ($origin), lines $first-$last of ${lines.size}; '$name' is at line $declarationLine\n$body",
            limit = MAX_SOURCE_CHARACTERS,
        )
    )
}

private fun analyzerOrNull(): ScriptingAnalyzer? = ScriptingEnvironment.currentOrNull()?.analyzer

private fun readScript(path: String): String {
    val file = ScriptRegistry.artifacts(path)?.sourceFile?.takeIf(File::isFile)
        ?: throw ToolInputException("There is no script source at '$path'")
    return file.readText()
}

private fun describe(path: String, text: String, diagnostics: List<Diagnostic>): String {
    val errors = diagnostics.count { it.severity.isError() }
    val warnings = diagnostics.size - errors
    if (diagnostics.isEmpty()) return "$path: no errors or warnings"
    val lines = text.lines()
    return buildString {
        appendLine("$path: $errors errors, $warnings warnings")
        diagnostics.sortedWith(compareBy({ it.range.start.line }, { it.range.start.column })).take(MAX_DIAGNOSTICS).forEach { diagnostic ->
            val start = diagnostic.range.start
            val severity = if (diagnostic.severity.isError()) "error" else "warning"
            if (start.line < 0) {
                appendLine("$severity: ${diagnostic.message}")
                return@forEach
            }
            appendLine("$severity ${start.line + 1}:${start.column + 1}: ${diagnostic.message}")
            lines.getOrNull(start.line)?.let { source -> appendLine("    | ${source.trimEnd()}") }
        }
        if (diagnostics.size > MAX_DIAGNOSTICS) appendLine("… ${diagnostics.size - MAX_DIAGNOSTICS} more")
    }.trimEnd()
}

private const val DEFAULT_SEARCH_LIMIT = 30
private const val MAX_SEARCH_LIMIT = 200
private const val DEFAULT_SOURCE_LINES = 200
private const val MAX_SOURCE_LINES = 1_000
private const val MAX_SOURCE_CHARACTERS = 80_000
private const val LINES_BEFORE_DECLARATION = 10
private const val MAX_DIAGNOSTICS = 100
