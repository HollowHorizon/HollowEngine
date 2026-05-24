package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.KatariNarrativeAnalysis
import com.sunnychung.lib.multiplatform.kotlite.katari.analyzeKatariNarrativeScript
import com.sunnychung.lib.multiplatform.kotlite.model.*
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.InlayHint
import ru.hollowhorizon.hollowengine.common.scripting.ide.*
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType
import ru.hollowhorizon.hollowengine.common.scripting.ide.ui.UiMarkupScriptingAnalyzer
import java.util.*

private val keywords = setOf(
    "as", "async", "break", "checkpoint", "choose", "class", "continue", "disableIf",
    "else", "enum", "false", "for", "fun", "if", "import", "in", "is", "jump",
    "load", "null", "race", "return", "true", "typealias", "val", "var", "when",
    "while", "with",
)
private val keywordCompletions = keywords.map { keywordCompletionItem(it) }
private val brackets = mapOf('(' to ')', '[' to ']', '{' to '}')
private const val MAX_COMPLETIONS = 80
private const val MIN_TOP_LEVEL_COMPLETION_PREFIX = 2
private val editorSymbols: KatariSymbols by lazy {
    runCatching {
        val bindings = createHollowKatariEditorBindings()
        KatariSymbols.from(analyzeKatariNarrativeScript("<editor>", "", bindings))
    }.getOrElse { KatariSymbols.empty() }
}

object KatariScriptingAnalyzer : ScriptingAnalyzer {
    val bindings = createHollowKatariEditorBindings()

    @Volatile
    private var cached: CachedAnalysis? = null

    override fun highlight(name: String, text: String, offset: Int): List<TextLine> {
        val analysis = analyze(name, text)
        val semanticRanges = analysis.result.getOrNull()?.let { semanticRanges(text, it.analysis) }.orEmpty()
        val occurrenceRanges = matchingIdentifierRanges(text, offset, semanticRanges)
        val bracketRanges = matchingBracketRanges(text, offset)
        val embeddedRanges = embeddedMarkupRanges(text) + embeddedStructRanges(text)
        val lineHints = analysis.result.getOrNull()?.let { inlayHints(text, it.analysis) }.orEmpty()
        return buildHighlightedLines(text, semanticRanges + embeddedRanges + occurrenceRanges + bracketRanges, lineHints)
    }

    override fun lightweightHighlightLine(name: String, line: String): TextLine {
        return TextLine(spansFor(line, tokenize(line)), ArrayList())
    }

    override fun completions(name: String, text: String, offset: Int): List<CompletionItem> {
        val context = CompletionContext.from(text, offset)
        if (!context.shouldOfferCompletions(text, offset)) return emptyList()
        val symbols = completionSymbols(name, text, offset, context)
        val candidates = if (context.receiverName == null) {
            buildList {
                addAll(keywordCompletions)
                addAll(symbols.globals.map { it.toCompletion(CompletionItemTag.LOCAL_VARIABLE) })
                addAll(symbols.localsBefore(offset).map { it.toCompletion(CompletionItemTag.LOCAL_VARIABLE) })
                addAll(symbols.functions.filter { it.receiverType == null }.map { it.toCompletion() })
                addAll(symbols.classes.map { it.toCompletion(CompletionItemTag.CLASS) })
            }
        } else {
            val receiverType = symbols.typeAt(context.receiverName, context.receiverStart)
                ?: symbols.localType(context.receiverName, offset)
                ?: KatariEditorContextGlobalTypes[context.receiverName]
                ?: context.receiverName.takeIf { it in symbols.enums.keys }?.let { "$it.Companion" }
            membersForReceiver(symbols, receiverType)
        }
        return candidates
            .filter { it.name.startsWith(context.prefix, ignoreCase = true) }
            .distinctBy { it.completionIdentity() }
            .sortedWith(compareBy<CompletionItem> { it.tag.ordinal }.thenBy { it.name })
            .take(MAX_COMPLETIONS)
    }

    override fun diagnostic(name: String, text: String): List<Diagnostic> {
        return analyze(name, text).result.fold(
            onSuccess = { emptyList() },
            onFailure = { listOf(it.toDiagnostic()) },
        )
    }

    private fun analyze(name: String, text: String): CachedAnalysis {
        cached?.takeIf { it.name == name && it.text == text }?.let { return it }
        return synchronized(this) {
            cached?.takeIf { it.name == name && it.text == text } ?: run {
                val result = runCatching {
                    val analysis = analyzeKatariNarrativeScript(name, text, bindings, HollowEngineSources(CodeSource("scripts/$name", text, "")))
                    AnalysisSnapshot(analysis, KatariSymbols.from(analysis))
                }
                CachedAnalysis(name, text, result).also { cached = it }
            }
        }
    }

    private fun completionSymbols(name: String, text: String, offset: Int, context: CompletionContext): KatariSymbols {
        analyze(name, text).result.getOrNull()?.let { return it.symbols }
        completionAnalysisTexts(text, offset, context).forEach { candidate ->
            analyze("$name<completion>", candidate).result.getOrNull()?.let { return it.symbols }
        }
        return editorSymbols
    }
}

    private fun semanticRanges(text: String, analysis: KatariNarrativeAnalysis): List<StyledRange> {
        val ranges = mutableListOf<StyledRange>()
        val lines = text.lines()
        analysis.semanticScript.walk { node, parent ->
            when (node) {
                is PropertyDeclarationNode -> {
                    ranges += StyledRange.identifier(lines, node.position, node.name, TokenType.PROPERTY_IDENTIFIER)
                }
                is FunctionDeclarationNode -> {
                    ranges += StyledRange.identifier(lines, node.position, node.name, TokenType.FUNCTION, bold = true)
                }
                is FunctionValueParameterNode -> {
                    ranges += StyledRange.identifier(lines, node.position, node.name, TokenType.PARAMETER)
                }
                is ValueParameterDeclarationNode -> {
                    ranges += StyledRange.identifier(lines, node.position, node.name, TokenType.PARAMETER)
                }
                is FunctionCallArgumentNode -> {
                    node.name?.let {
                        ranges += StyledRange.identifier(lines, node.position, it, TokenType.VALUE_ARGUMENT_NAME)
                    }
                }
                is VariableReferenceNode -> {
                    val type = when {
                        parent is FunctionCallNode && parent.function === node -> TokenType.FUNCTION
                        node.variableName.firstOrNull()?.isUpperCase() == true -> TokenType.CLASS
                        else -> TokenType.VARIABLE
                    }
                    ranges += StyledRange.identifier(lines, node.position, node.variableName, type)
                }
                is ClassMemberReferenceNode -> {
                    val token = if (parent is NavigationNode && parent.isCallTarget(analysis.semanticScript)) {
                        TokenType.METHOD
                    } else {
                        TokenType.FIELD
                    }
                    ranges += StyledRange.identifier(lines, node.position, node.name, token)
                }
                is ClassDeclarationNode -> {
                    ranges += StyledRange.identifier(lines, node.position, node.name, TokenType.ENUM, bold = true)
                }
                else -> Unit
            }
        }
        return ranges
    }

    private fun inlayHints(text: String, analysis: KatariNarrativeAnalysis): Map<Int, List<InlayHint>> {
        val hintsByLine = linkedMapOf<Int, MutableList<InlayHint>>()
        val lines = text.lines()
        analysis.semanticScript.walk { node, _ ->
            when (node) {
                is PropertyDeclarationNode -> {
                    if (node.declaredType == null) {
                        val type = node.inferredType?.descriptiveName() ?: return@walk
                        hintsByLine.add(lines, node.position, node.name, ": $type")
                    }
                }
                is FunctionValueParameterNode -> {
                    if (node.declaredType == null) {
                        val type = node.inferredType?.descriptiveName() ?: return@walk
                        hintsByLine.add(lines, node.position, node.name, ": $type")
                    }
                }
                is ValueParameterDeclarationNode -> {
                    if (node.declaredType == null) {
                        val type = node.inferredType?.descriptiveName() ?: return@walk
                        hintsByLine.add(lines, node.position, node.name, ": $type")
                    }
                }
                else -> Unit
            }
        }
        return hintsByLine
    }

    private fun MutableMap<Int, MutableList<InlayHint>>.add(lines: List<String>, position: SourcePosition, name: String, text: String) {
        if (position.lineNum <= 0 || position.col <= 0) return
        val line = position.lineNum - 1
        val start = identifierStart(lines, position, name) ?: return
        getOrPut(line) { mutableListOf() } += InlayHint(start + name.length, text)
    }

    private fun membersForReceiver(symbols: KatariSymbols, receiverType: String?): List<CompletionItem> {
        val cleanType = receiverType?.removeSuffix("?") ?: return emptyList()
        val enumType = cleanType.removeSuffix(".Companion")
        return buildList {
            addAll(symbols.functions.filter { symbols.acceptsReceiver(it.receiverType, cleanType) }.map { it.toCompletion() })
            addAll(symbols.properties.filter { symbols.acceptsReceiver(it.receiverType, cleanType) }.map { it.toCompletion() })
            symbols.enums[enumType]?.let { entries ->
                add(declarationCompletionItem {
                    show = "entries"
                    insert = "entries"
                    name = "entries"
                    tag = CompletionItemTag.PROPERTY
                    tail = "List<$enumType>"
                })
                add(declarationCompletionItem {
                    show = "valueOf"
                    insert = "valueOf(\"\")"
                    name = "valueOf"
                    tag = CompletionItemTag.FUNCTION
                    tail = enumType
                    moveCaret = -2
                })
                entries.forEach { entry ->
                    add(declarationCompletionItem {
                        show = entry
                        insert = entry
                        name = entry
                        tag = CompletionItemTag.PROPERTY
                        tail = enumType
                    })
                }
            }
        }
    }

    private fun buildHighlightedLines(
        text: String,
        semanticRanges: List<StyledRange>,
        hints: Map<Int, List<InlayHint>>,
    ): List<TextLine> {
        val byLine = semanticRanges.groupBy { it.line }
        return text.lines().mapIndexed { lineIndex, line ->
            val lexical = tokenize(line)
            val semantic = byLine[lineIndex].orEmpty()
            val ranges = lexical.sortedWith(compareBy<StyledRange> { it.start }.thenBy { -it.end }) +
                    semantic.sortedWith(compareBy<StyledRange> { it.start }.thenBy { -it.end })
            TextLine(spansFor(line, ranges), ArrayList(hints[lineIndex].orEmpty()))
        }
    }

    private fun tokenize(line: String, baseOffset: Int = 0): List<StyledRange> {
        val ranges = mutableListOf<StyledRange>()
        var i = 0
        while (i < line.length) {
            val start = i
            when {
                line.startsWith("//", i) -> {
                    ranges += StyledRange(0, baseOffset + i, baseOffset + line.length, style(TokenType.COMMENT), priority = 4)
                    break
                }
                line[i] == '"' || line[i] == '\'' -> {
                    val quote = line[i++]
                    val templates = mutableListOf<StyledRange>()
                    while (i < line.length) {
                        when {
                            line[i] == '\\' && i + 1 < line.length -> i += 2
                            quote == '"' && line[i] == '$' && i + 1 < line.length && line[i + 1] == '{' -> {
                                val templateStart = i
                                val expressionStart = i + 2
                                val expressionEnd = findTemplateExpressionEnd(line, expressionStart)
                                templates += StyledRange(0, baseOffset + templateStart, baseOffset + expressionStart, style(TokenType.KEYWORD), priority = 5)
                                if (expressionEnd >= expressionStart) {
                                    templates += tokenizeTemplateExpression(line.substring(expressionStart, expressionEnd), baseOffset + expressionStart)
                                    templates += StyledRange(0, baseOffset + expressionEnd, baseOffset + expressionEnd + 1, style(TokenType.KEYWORD), priority = 5)
                                    i = expressionEnd + 1
                                } else {
                                    i = expressionStart
                                }
                            }
                            line[i++] == quote -> break
                        }
                    }
                    ranges += StyledRange(0, baseOffset + start, baseOffset + i, style(TokenType.STRING), priority = 4)
                    ranges += templates
                }
                line[i].isDigit() -> {
                    while (i < line.length && (line[i].isDigit() || line[i] == '.')) i++
                    ranges += StyledRange(0, baseOffset + start, baseOffset + i, style(TokenType.NUMERIC_LITERAL), priority = 1)
                }
                line[i].isIdentifierStart() -> {
                    i++
                    while (i < line.length && line[i].isIdentifierPart()) i++
                    val word = line.substring(start, i)
                    if (word in keywords) ranges += StyledRange(0, baseOffset + start, baseOffset + i, style(TokenType.KEYWORD), priority = 1)
                }
                else -> i++
            }
        }
        return ranges
    }

    private fun tokenizeTemplateExpression(expression: String, baseOffset: Int): List<StyledRange> {
        val ranges = mutableListOf<StyledRange>()
        var index = 0
        var afterDot = false
        while (index < expression.length) {
            val start = index
            when {
                expression[index].isDigit() -> {
                    while (index < expression.length && (expression[index].isDigit() || expression[index] == '.')) index++
                    ranges += StyledRange(0, baseOffset + start, baseOffset + index, style(TokenType.NUMERIC_LITERAL), priority = 5)
                    afterDot = false
                }
                expression[index].isIdentifierStart() -> {
                    index++
                    while (index < expression.length && expression[index].isIdentifierPart()) index++
                    val word = expression.substring(start, index)
                    val token = when {
                        word in keywords -> TokenType.KEYWORD
                        afterDot -> TokenType.FIELD
                        word.firstOrNull()?.isUpperCase() == true -> TokenType.CLASS
                        else -> TokenType.VARIABLE
                    }
                    ranges += StyledRange(0, baseOffset + start, baseOffset + index, style(token), priority = 5)
                    afterDot = false
                }
                expression[index] == '.' -> {
                    afterDot = true
                    index++
                }
                else -> {
                    afterDot = false
                    index++
                }
            }
        }
        return ranges
    }

    private fun findTemplateExpressionEnd(line: String, start: Int): Int {
        var index = start
        var depth = 1
        while (index < line.length) {
            when (line[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return -1
    }

    private fun spansFor(line: String, ranges: List<StyledRange>): List<Pair<String, SpanStyle>> {
        if (line.isEmpty()) return listOf("" to style(TokenType.DEFAULT))
        val styles = Array(line.length) { style(TokenType.DEFAULT) }
        ranges.sortedBy { it.priority }.forEach { range ->
            val start = range.start.coerceIn(0, line.length)
            val end = range.end.coerceIn(start, line.length)
            for (index in start until end) styles[index] = range.style
        }
        val spans = mutableListOf<Pair<String, SpanStyle>>()
        var start = 0
        var current = styles[0]
        for (index in 1 until line.length) {
            if (styles[index] != current) {
                spans += line.substring(start, index) to current
                start = index
                current = styles[index]
            }
        }
        spans += line.substring(start) to current
        return spans
    }

    private fun embeddedMarkupRanges(text: String): List<StyledRange> {
        return xmlLiteralRanges(text).flatMap { range ->
            val fragment = text.substring(range.start, range.end)
            UiMarkupScriptingAnalyzer.highlight("<katari-ui-literal>", fragment, 0)
                .flatMapIndexed { lineIndex, line ->
                    var column = 0
                    line.spans.map { (token, style) ->
                        val mapped = range.positionAt(text, lineIndex, column)
                        column += token.length
                        StyledRange(mapped.line, mapped.column, mapped.column + token.length, style, priority = 3)
                    }
                }
        }
    }

    private fun embeddedStructRanges(text: String): List<StyledRange> {
        return structLiteralRanges(text).flatMap { range ->
            val result = mutableListOf<StyledRange>()
            var index = range.start
            while (index < range.end) {
                val char = text[index]
                when {
                    char == '"' || char == '\'' -> {
                        val end = stringEnd(text, index).coerceAtMost(range.end)
                        val start = absolutePosition(text, index)
                        val stop = absolutePosition(text, end)
                        if (start.line == stop.line) {
                            result += StyledRange(start.line, start.column, stop.column, style(TokenType.STRING), priority = 3)
                        }
                        index = end
                    }
                    char.isDigit() || char == '-' -> {
                        val end = numericEnd(text, index).coerceAtMost(range.end)
                        val start = absolutePosition(text, index)
                        val stop = absolutePosition(text, end)
                        if (start.line == stop.line) {
                            result += StyledRange(start.line, start.column, stop.column, style(TokenType.NUMERIC_LITERAL), priority = 3)
                        }
                        index = end
                    }
                    char.isIdentifierStart() -> {
                        val end = identifierEnd(text, index).coerceAtMost(range.end)
                        val colon = skipWhitespace(text, end)
                        if (colon < range.end && text[colon] == ':') {
                            val start = absolutePosition(text, index)
                            val stop = absolutePosition(text, end)
                            if (start.line == stop.line) {
                                result += StyledRange(start.line, start.column, stop.column, style(TokenType.PROPERTY_IDENTIFIER), priority = 3)
                            }
                        }
                        index = end
                    }
                    else -> index++
                }
            }
            result
        }
    }

    private fun xmlLiteralRanges(text: String): List<TextRange> {
        val ranges = mutableListOf<TextRange>()
        var index = 0
        var inLineComment = false
        var inSingleQuote = false
        var inDoubleQuote = false
        var escaped = false
        while (index < text.length) {
            val char = text[index]
            val next = text.getOrNull(index + 1)
            when {
                inLineComment && char == '\n' -> inLineComment = false
                inLineComment -> Unit
                inSingleQuote -> {
                    if (!escaped && char == '\'') inSingleQuote = false
                    escaped = !escaped && char == '\\'
                }
                inDoubleQuote -> {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> inDoubleQuote = false
                    }
                }
                char == '/' && next == '/' -> {
                    inLineComment = true
                    index++
                }
                char == '\'' -> inSingleQuote = true
                char == '"' -> inDoubleQuote = true
                char == '<' && next.isXmlTagStart() -> {
                    val end = findXmlLiteralEnd(text, index)
                    if (end > index) {
                        ranges += TextRange(index, end)
                        index = end - 1
                    }
                }
            }
            index++
        }
        return ranges
    }

    private fun findXmlLiteralEnd(text: String, start: Int): Int {
        var index = start
        var depth = 0
        while (index < text.length) {
            val open = text.indexOf('<', index)
            if (open < 0) return index
            val close = text.indexOf('>', open + 1)
            if (close < 0) return text.length
            val body = text.substring(open + 1, close).trim()
            when {
                body.startsWith("!--") -> Unit
                body.startsWith("/") -> {
                    depth--
                    if (depth <= 0) return close + 1
                }
                body.endsWith("/") -> {
                    if (depth == 0) return close + 1
                }
                else -> depth++
            }
            index = close + 1
        }
        return text.length
    }

    private fun structLiteralRanges(text: String): List<TextRange> {
        val ranges = mutableListOf<TextRange>()
        var index = 0
        while (index < text.length) {
            val start = text.indexOf("struct", index)
            if (start < 0) break
            val before = text.getOrNull(start - 1)
            val after = text.getOrNull(start + "struct".length)
            if (before.isIdentifierPartOrNull() || after.isIdentifierPartOrNull()) {
                index = start + 1
                continue
            }
            val open = skipWhitespace(text, start + "struct".length)
            if (open >= text.length || text[open] != '{') {
                index = start + 1
                continue
            }
            val close = matchingBrace(text, open) ?: break
            ranges += TextRange(start, close + 1)
            index = close + 1
        }
        return ranges
    }

    private fun matchingBrace(text: String, open: Int): Int? {
        var index = open
        var depth = 0
        while (index < text.length) {
            when (text[index]) {
                '"', '\'' -> index = stringEnd(text, index) - 1
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return null
    }

    private fun stringEnd(text: String, start: Int): Int {
        val quote = text[start]
        var index = start + 1
        while (index < text.length) {
            if (text[index] == '\\') {
                index += 2
            } else if (text[index] == quote) {
                return index + 1
            } else {
                index++
            }
        }
        return text.length
    }

    private fun numericEnd(text: String, start: Int): Int {
        var index = start
        if (text.getOrNull(index) == '-') index++
        while (index < text.length && (text[index].isDigit() || text[index] == '.')) index++
        return index
    }

    private fun identifierEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isIdentifierPart()) index++
        return index
    }

    private fun skipWhitespace(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }

    private fun Char?.isXmlTagStart(): Boolean {
        return this != null && (isLetter() || this == '/' || this == '!' || this == '?')
    }

    private fun Char?.isIdentifierPartOrNull(): Boolean {
        return this != null && isIdentifierPart()
    }

    private fun matchingBracketRanges(text: String, offset: Int): List<StyledRange> {
        val index = listOf(offset - 1, offset).firstOrNull { it in text.indices && (text[it] in brackets.keys || text[it] in brackets.values) }
            ?: return emptyList()
        val bracket = text[index]
        val pair = if (bracket in brackets.keys) bracket to brackets.getValue(bracket) else brackets.entries.first { it.value == bracket }.toPair()
        val direction = if (bracket == pair.first) 1 else -1
        var depth = 0
        var i = index
        while (i in text.indices) {
            val char = text[i]
            if (char == pair.first) depth += direction
            if (char == pair.second) depth -= direction
            if (depth == 0 && i != index) {
                return listOf(index, i).mapNotNull { absolute ->
                    val pos = absolutePosition(text, absolute)
                    StyledRange(pos.line, pos.column, pos.column + 1, style(TokenType.DEFAULT, highlight = true, bold = true), priority = 6)
                }
            }
            i += direction
        }
        return emptyList()
    }

    private fun matchingIdentifierRanges(text: String, offset: Int, semanticRanges: List<StyledRange>): List<StyledRange> {
        val selected = identifierAt(text, offset) ?: return emptyList()
        val lines = text.lines()
        return semanticRanges.mapNotNull { range ->
            val line = lines.getOrNull(range.line) ?: return@mapNotNull null
            val token = line.substring(range.start.coerceIn(0, line.length), range.end.coerceIn(0, line.length))
            if (token != selected || !range.style.color.isIdentifierToken()) return@mapNotNull null
            range.copy(style = range.style.copy(highlight = true), priority = 7)
        }
    }

    private fun identifierAt(text: String, offset: Int): String? {
        if (text.isEmpty()) return null
        val safeOffset = offset.coerceIn(0, text.length)
        val probe = when {
            safeOffset < text.length && text[safeOffset].isIdentifierPart() -> safeOffset
            safeOffset > 0 && text[safeOffset - 1].isIdentifierPart() -> safeOffset - 1
            else -> return null
        }
        var start = probe
        while (start > 0 && text[start - 1].isIdentifierPart()) start--
        var end = probe + 1
        while (end < text.length && text[end].isIdentifierPart()) end++
        return text.substring(start, end).takeIf { it.firstOrNull()?.isIdentifierStart() == true }
    }

    private fun Throwable.toDiagnostic(): Diagnostic {
        val message = message ?: javaClass.simpleName
        val match = Regex(""":(\d+):(\d+)]""").find(message)
        val line = (match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
        val col = (match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
        return Diagnostic(
            Range(Position(line, col), Position(line, col + 1)),
            Severity.ERROR,
            message.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() },
        )
    }

    private fun List<CompletionItem>.filterByPrefix(text: String, offset: Int): List<CompletionItem> {
        val prefix = CompletionContext.from(text, offset).prefix
        return filter { it.name.startsWith(prefix, ignoreCase = true) }
    }

    private fun CompletionItem.completionIdentity(): List<String?> {
        return when (this) {
            is CompletionItem.Declaration -> listOf(name, show, tag.name, middle, tail)
            is CompletionItem.Keyword -> listOf(name, show, tag.name)
        }
    }

    private fun style(
        token: TokenType,
        italic: Boolean = false,
        bold: Boolean = false,
        highlight: Boolean = false,
    ) = SpanStyle(token, italic = italic, bold = bold, highlight = highlight)

    private fun Char.isIdentifierStart() = this == '_' || isLetter()
    private fun Char.isIdentifierPart() = this == '_' || isLetterOrDigit()

    private fun TokenType.isIdentifierToken(): Boolean {
        return when (this) {
            TokenType.PROPERTY_IDENTIFIER,
            TokenType.FIELD,
            TokenType.VARIABLE,
            TokenType.EXTENSION_RECEIVER,
            TokenType.VALUE_ARGUMENT_NAME,
            TokenType.PARAMETER,
            TokenType.NAME_REFERENCE,
            TokenType.TOP_LEVEL -> true
            else -> false
        }
    }

    private data class CachedAnalysis(
        val name: String,
        val text: String,
        val result: Result<AnalysisSnapshot>,
    )

    private data class AnalysisSnapshot(
        val analysis: KatariNarrativeAnalysis,
        val symbols: KatariSymbols,
    )

    private data class StyledRange(
        val line: Int,
        val start: Int,
        val end: Int,
        val style: SpanStyle,
        val priority: Int = 0,
    ) {
        companion object {
            fun identifier(lines: List<String>, position: SourcePosition, text: String, token: TokenType, bold: Boolean = false): StyledRange {
                val line = position.lineNum - 1
                val start = identifierStart(lines, position, text) ?: (position.col - 1).coerceAtLeast(0)
                return StyledRange(line, start, start + text.length, style(token, bold = bold), priority = 2)
            }
        }
    }

    private data class TextRange(
        val start: Int,
        val end: Int,
    ) {
        fun positionAt(text: String, lineOffset: Int, columnOffset: Int): Position {
            val base = absolutePosition(text, start)
            if (lineOffset == 0) return Position(base.line, base.column + columnOffset)
            return Position(base.line + lineOffset, columnOffset)
        }
    }

    private data class SymbolEntry(
        val name: String,
        val type: String?,
        val positionIndex: Int = Int.MIN_VALUE,
    ) {
        fun toCompletion(tag: CompletionItemTag): CompletionItem.Declaration {
            return declarationCompletionItem {
                show = this@SymbolEntry.name
                insert = this@SymbolEntry.name
                this.name = this@SymbolEntry.name
                this.tag = tag
                type?.let { tail = it }
            }
        }
    }

    private data class FunctionEntry(
        val name: String,
        val receiverType: String?,
        val typeParameters: List<TypeParameterEntry>,
        val parameters: List<ParameterEntry>,
        val returnType: String?,
    ) {
        fun toCompletion(): CompletionItem.Declaration {
            val insertText = "${this@FunctionEntry.name}()"
            return declarationCompletionItem {
                show = this@FunctionEntry.name
                insert = insertText
                this.name = this@FunctionEntry.name
                tag = CompletionItemTag.FUNCTION
                middle = typeParameters.display() + parameters.joinToString(prefix = "(", postfix = ")") { it.display() }
                returnType?.let { tail = it }
                moveCaret = if (parameters.isEmpty()) 0 else -1
            }
        }
    }

    private data class TypeParameterEntry(val name: String, val upperBound: String?) {
        fun display(): String = upperBound?.let { "$name : $it" } ?: name
    }

    private fun List<TypeParameterEntry>.display(): String {
        if (isEmpty()) return ""
        return joinToString(prefix = "<", postfix = ">") { it.display() }
    }

    private data class ParameterEntry(val name: String, val type: String?) {
        fun display(): String = if (type == null) name else "$name: $type"
    }

    private data class PropertyEntry(
        val name: String,
        val receiverType: String,
        val type: String?,
    ) {
        fun toCompletion(): CompletionItem.Declaration {
            return declarationCompletionItem {
                show = this@PropertyEntry.name
                insert = this@PropertyEntry.name
                this.name = this@PropertyEntry.name
                tag = CompletionItemTag.PROPERTY
                type?.let { tail = it }
            }
        }
    }

    private data class KatariSymbols(
        val globals: List<SymbolEntry>,
        val locals: List<SymbolEntry>,
        val functions: List<FunctionEntry>,
        val properties: List<PropertyEntry>,
        val classes: List<SymbolEntry>,
        val enums: Map<String, List<String>>,
        val typedRanges: List<TypedRange>,
        val superTypes: Map<String, Set<String>>,
    ) {
        fun localsBefore(offset: Int): List<SymbolEntry> = locals.filter { it.positionIndex < offset }

        fun localType(name: String, offset: Int): String? {
            return (localsBefore(offset) + globals).lastOrNull { it.name == name }?.type
        }

        fun typeAt(name: String, start: Int): String? {
            return typedRanges.firstOrNull { it.name == name && it.start == start }?.type
        }

        fun acceptsReceiver(expectedType: String?, actualType: String): Boolean {
            val expected = expectedType?.cleanType() ?: return false
            val actual = actualType.cleanType()
            return expected == actual || expected == "Any" || expected == "Any?" ||
                    supertypesOf(actual).any { it.cleanType() == expected }
        }

        private fun supertypesOf(type: String): Set<String> {
            val result = linkedSetOf<String>()
            fun visit(current: String) {
                superTypes[current.cleanType()].orEmpty().forEach { parent ->
                    if (result.add(parent)) visit(parent)
                }
            }
            visit(type)
            return result
        }

        companion object {
            fun empty(): KatariSymbols {
                return KatariSymbols(
                    globals = emptyList(),
                    locals = emptyList(),
                    functions = emptyList(),
                    properties = emptyList(),
                    classes = emptyList(),
                    enums = emptyMap(),
                    typedRanges = emptyList(),
                    superTypes = emptyMap(),
                )
            }

            fun from(analysis: KatariNarrativeAnalysis): KatariSymbols {
                val symbolTable = analysis.semanticAnalyzer.symbolTable
                val environment = analysis.semanticAnalyzer.executionEnvironment
                val globals = environment.getGlobalProperties(symbolTable).map {
                    SymbolEntry(it.declaredName, it.type)
                }
                val functions = environment.getBuiltinFunctions(symbolTable).map {
                    FunctionEntry(
                        name = it.name,
                        receiverType = it.receiver?.descriptiveName(),
                        typeParameters = it.typeParameters.map { parameter ->
                            TypeParameterEntry(parameter.name, parameter.typeUpperBound?.descriptiveName())
                        },
                        parameters = it.valueParameters.map { parameter ->
                            ParameterEntry(parameter.name, parameter.declaredType?.descriptiveName())
                        },
                        returnType = it.declaredReturnType?.descriptiveName(),
                    )
                }
                val properties = environment.getExtensionProperties(symbolTable).map {
                    PropertyEntry(it.declaredName, it.receiver, it.type)
                }
                val classes = environment.getBuiltinClasses(symbolTable)
                    .map { SymbolEntry(it.fullQualifiedName.removeSuffix("?"), null) }
                    .distinctBy { it.name }
                    .filterNot { it.name.endsWith(".Companion") }
                val superTypes = environment.getBuiltinClasses(symbolTable).associate { clazz ->
                    val parents = buildSet {
                        clazz.superClass?.fullQualifiedName?.let(::add)
                        (clazz.superClassInvocation?.function as? TypeNode)?.let { add(it.descriptiveName()) }
                        clazz.superInterfaces.mapTo(this) { it.fullQualifiedName }
                        clazz.superInterfaceTypes.mapTo(this) { it.descriptiveName() }
                    }
                    clazz.fullQualifiedName.removeSuffix("?") to parents
                } + generatedKatariTypeSuperTypes()
                val locals = mutableListOf<SymbolEntry>()
                val typedRanges = mutableListOf<TypedRange>()
                analysis.semanticScript.walk { node, _ ->
                    when (node) {
                        is PropertyDeclarationNode -> locals += SymbolEntry(
                            node.name,
                            node.type.descriptiveName(),
                            node.position.index,
                        )
                        is FunctionValueParameterNode -> locals += SymbolEntry(
                            node.name,
                            node.type.descriptiveName(),
                            node.position.index,
                        )
                        is ValueParameterDeclarationNode -> locals += SymbolEntry(
                            node.name,
                            node.type.descriptiveName(),
                            node.position.index,
                        )
                        is VariableReferenceNode -> typedRanges += TypedRange(
                            node.variableName,
                            node.position.index,
                            node.variableName.length,
                            node.type?.descriptiveName(),
                        )
                        else -> Unit
                    }
                }
                return KatariSymbols(
                    globals = globals,
                    locals = locals.distinctBy { it.name to it.positionIndex },
                    functions = functions,
                    properties = properties,
                    classes = classes,
                    enums = analysis.analysisEnumEntries(),
                    typedRanges = typedRanges,
                    superTypes = superTypes,
                )
            }
        }
    }

    private data class TypedRange(
        val name: String,
        val start: Int,
        val length: Int,
        val type: String?,
    )

    private data class CompletionContext(
        val prefix: String,
        val receiverName: String?,
        val receiverStart: Int,
        val prefixStart: Int,
    ) {
        fun shouldOfferCompletions(text: String, offset: Int): Boolean {
            if (isInsideSuppressedText(text, offset)) return false
            if (receiverName != null) return true
            return prefix.length >= MIN_TOP_LEVEL_COMPLETION_PREFIX
        }

        companion object {
            fun from(text: String, offset: Int): CompletionContext {
                val safeOffset = offset.coerceIn(0, text.length)
                val prefixStart = scanIdentifierStart(text, safeOffset)
                val prefix = text.substring(prefixStart, safeOffset)
                val dot = prefixStart - 1
                if (dot >= 0 && text[dot] == '.') {
                    val receiverEnd = dot
                    val receiverStart = scanIdentifierStart(text, receiverEnd)
                    val receiver = text.substring(receiverStart, receiverEnd)
                    return CompletionContext(prefix, receiver.takeIf { it.isNotBlank() }, receiverStart, prefixStart)
                }
                return CompletionContext(prefix, null, -1, prefixStart)
            }

            private fun scanIdentifierStart(text: String, offset: Int): Int {
                var index = offset
                while (index > 0 && text[index - 1].isIdentifierPart()) index--
                return index
            }
        }
    }

    private fun isInsideSuppressedText(text: String, offset: Int): Boolean {
        val safeOffset = offset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (safeOffset - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        var index = lineStart
        var inSingleQuote = false
        var inDoubleQuote = false
        var escaped = false
        var templateDepth = 0

        while (index < safeOffset) {
            val char = text[index]
            val next = text.getOrNull(index + 1)

            when {
                inSingleQuote -> {
                    if (!escaped && char == '\'') inSingleQuote = false
                    escaped = !escaped && char == '\\'
                }
                inDoubleQuote && templateDepth == 0 -> {
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '$' && next == '{' -> {
                            templateDepth = 1
                            index++
                        }
                        char == '"' -> inDoubleQuote = false
                    }
                }
                inDoubleQuote -> {
                    when (char) {
                        '{' -> templateDepth++
                        '}' -> templateDepth = (templateDepth - 1).coerceAtLeast(0)
                    }
                }
                char == '/' && next == '/' -> return true
                char == '\'' -> inSingleQuote = true
                char == '"' -> inDoubleQuote = true
            }
            index++
        }

        return inSingleQuote || (inDoubleQuote && templateDepth == 0)
    }

    private fun KatariNarrativeAnalysis.analysisEnumEntries(): Map<String, List<String>> {
        return enumDefinitions.mapValues { (_, definition) -> definition.entries.map { it.entryName } }
    }

    private fun completionAnalysisTexts(text: String, offset: Int, context: CompletionContext): List<String> {
        val safeOffset = offset.coerceIn(0, text.length)
        val prefixStart = safeOffset - context.prefix.length
        val variants = linkedSetOf<String>()

        if (context.receiverName != null && context.receiverStart in 0..safeOffset) {
            variants += text.replaceRange(context.receiverStart, safeOffset, context.receiverName)
        }
        if (prefixStart in 0..safeOffset) {
            variants += text.replaceRange(prefixStart, safeOffset, "")
            variants += text.replaceRange(prefixStart, safeOffset, "null")
            val lineStart = text.lastIndexOf('\n', (prefixStart - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
            val lineEnd = text.indexOf('\n', safeOffset).let { if (it < 0) text.length else it }
            variants += text.replaceRange(lineStart, lineEnd, "")
        }
        return variants.filter { it != text }
    }

    private fun String.cleanType(): String {
        return removeSuffix("?").substringBefore('<')
    }

    private fun identifierStart(lines: List<String>, position: SourcePosition, name: String): Int? {
        val line = lines.getOrNull(position.lineNum - 1) ?: return null
        val searchStart = (position.col - 1).coerceIn(0, line.length)
        val direct = line.indexOf(name, searchStart)
        if (direct >= 0) return direct
        val before = line.lastIndexOf(name, searchStart)
        return before.takeIf { it >= 0 }
    }

    private fun absolutePosition(text: String, offset: Int): Position {
        var line = 0
        var column = 0
        for (index in 0 until offset.coerceIn(0, text.length)) {
            if (text[index] == '\n') {
                line++
                column = 0
            } else {
                column++
            }
        }
        return Position(line, column)
    }

    private fun NavigationNode.isCallTarget(root: ASTNode): Boolean {
        var result = false
        root.walk { node, _ ->
            if (node is FunctionCallNode && node.function === this) result = true
        }
        return result
    }

    private fun ASTNode.walk(visitor: (ASTNode, ASTNode?) -> Unit) {
        fun ASTNode.visit(parent: ASTNode?) {
            visitor(this, parent)
            children().forEach { it.visit(this) }
        }
        visit(null)
    }

    private fun ASTNode.children(): List<ASTNode> {
        return when (this) {
            is ScriptNode -> nodes
            is BlockNode -> statements
            is PropertyDeclarationNode -> listOfNotNull(initialValue)
            is FunctionDeclarationNode -> valueParameters + listOfNotNull(body)
            is FunctionValueParameterNode -> listOfNotNull(defaultValue)
            is FunctionCallNode -> listOf(function) + declaredTypeArguments + arguments
            is FunctionCallArgumentNode -> listOf(value)
            is NavigationNode -> listOf(subject, member)
            is AssignmentNode -> listOf(subject, value)
            is BinaryOpNode -> listOf(node1, node2)
            is UnaryOpNode -> listOfNotNull(node)
            is InfixFunctionCallNode -> listOf(node1, node2)
            is ElvisOpNode -> listOf(primaryNode, fallbackNode)
            is IfNode -> listOfNotNull(condition, trueBlock, falseBlock)
            is WhileNode -> listOfNotNull(condition, body)
            is ForNode -> variables + listOf(subject, body)
            is ReturnNode -> listOfNotNull(value)
            is IndexOpNode -> listOf(subject) + arguments
            is LambdaLiteralNode -> declaredValueParameters + body
            is StringNode -> nodes
            is ThrowNode -> listOf(value)
            is TryNode -> listOf(mainBlock) + catchBlocks + listOfNotNull(finallyBlock)
            is CatchNode -> listOf(block)
            is WhenNode -> listOfNotNull(subject) + entries
            is WhenSubjectNode -> listOf(value)
            is WhenEntryNode -> conditions + body
            is WhenConditionNode -> listOf(expression)
            is NarrativeChooseNode -> entries
            is NarrativeChooseEntryNode -> listOfNotNull(text, visibleCondition, disableCondition, disabledText, action)
            is NarrativeAsyncNode -> listOf(body)
            is NarrativeRaceNode -> entries
            is NarrativeRaceEntryNode -> listOf(action, result)
            else -> emptyList()
        }
    }
