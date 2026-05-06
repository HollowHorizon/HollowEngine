package ru.hollowhorizon.hollowengine.common.scripting.katari

import com.sunnychung.lib.multiplatform.kotlite.katari.KatariNarrativeAnalysis
import com.sunnychung.lib.multiplatform.kotlite.katari.analyzeKatariNarrativeScript
import com.sunnychung.lib.multiplatform.kotlite.model.ASTNode
import com.sunnychung.lib.multiplatform.kotlite.model.AssignmentNode
import com.sunnychung.lib.multiplatform.kotlite.model.BinaryOpNode
import com.sunnychung.lib.multiplatform.kotlite.model.BlockNode
import com.sunnychung.lib.multiplatform.kotlite.model.CatchNode
import com.sunnychung.lib.multiplatform.kotlite.model.ClassDeclarationNode
import com.sunnychung.lib.multiplatform.kotlite.model.ClassMemberReferenceNode
import com.sunnychung.lib.multiplatform.kotlite.model.ElvisOpNode
import com.sunnychung.lib.multiplatform.kotlite.model.ForNode
import com.sunnychung.lib.multiplatform.kotlite.model.FunctionCallArgumentNode
import com.sunnychung.lib.multiplatform.kotlite.model.FunctionCallNode
import com.sunnychung.lib.multiplatform.kotlite.model.FunctionDeclarationNode
import com.sunnychung.lib.multiplatform.kotlite.model.FunctionValueParameterNode
import com.sunnychung.lib.multiplatform.kotlite.model.IfNode
import com.sunnychung.lib.multiplatform.kotlite.model.IndexOpNode
import com.sunnychung.lib.multiplatform.kotlite.model.InfixFunctionCallNode
import com.sunnychung.lib.multiplatform.kotlite.model.LambdaLiteralNode
import com.sunnychung.lib.multiplatform.kotlite.model.NavigationNode
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeAsyncNode
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeChooseEntryNode
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeChooseNode
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeRaceEntryNode
import com.sunnychung.lib.multiplatform.kotlite.model.NarrativeRaceNode
import com.sunnychung.lib.multiplatform.kotlite.model.PropertyDeclarationNode
import com.sunnychung.lib.multiplatform.kotlite.model.ReturnNode
import com.sunnychung.lib.multiplatform.kotlite.model.ScriptNode
import com.sunnychung.lib.multiplatform.kotlite.model.SourcePosition
import com.sunnychung.lib.multiplatform.kotlite.model.StringNode
import com.sunnychung.lib.multiplatform.kotlite.model.ThrowNode
import com.sunnychung.lib.multiplatform.kotlite.model.TryNode
import com.sunnychung.lib.multiplatform.kotlite.model.TypeNode
import com.sunnychung.lib.multiplatform.kotlite.model.UnaryOpNode
import com.sunnychung.lib.multiplatform.kotlite.model.ValueParameterDeclarationNode
import com.sunnychung.lib.multiplatform.kotlite.model.VariableReferenceNode
import com.sunnychung.lib.multiplatform.kotlite.model.WhenConditionNode
import com.sunnychung.lib.multiplatform.kotlite.model.WhenEntryNode
import com.sunnychung.lib.multiplatform.kotlite.model.WhenNode
import com.sunnychung.lib.multiplatform.kotlite.model.WhenSubjectNode
import com.sunnychung.lib.multiplatform.kotlite.model.WhileNode
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.InlayHint
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItemTag
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.Position
import ru.hollowhorizon.hollowengine.common.scripting.ide.Range
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity
import ru.hollowhorizon.hollowengine.common.scripting.ide.SpanStyle
import ru.hollowhorizon.hollowengine.common.scripting.ide.TextLine
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType
import ru.hollowhorizon.hollowengine.common.scripting.ide.declarationCompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.keywordCompletionItem
import java.util.Locale

private val keywords = setOf(
    "as", "async", "break", "checkpoint", "choose", "class", "continue", "disableIf",
    "else", "enum", "false", "for", "fun", "if", "import", "in", "is", "jump",
    "load", "null", "race", "return", "true", "typealias", "val", "var", "when",
    "while", "with",
)
private val keywordCompletions = keywords.map { keywordCompletionItem(it) }
private val brackets = mapOf('(' to ')', '[' to ']', '{' to '}')
private const val MAX_COMPLETIONS = 80
private val editorSymbols: KatariSymbols by lazy {
    runCatching {
        val bindings = createHollowKatariEditorBindings()
        KatariSymbols.from(analyzeKatariNarrativeScript("<editor>", "", bindings))
    }.getOrElse { KatariSymbols.empty() }
}

object KatariScriptingAnalyzer : ScriptingAnalyzer {
    @Volatile
    private var cached: CachedAnalysis? = null

    override fun highlight(name: String, text: String, offset: Int): List<TextLine> {
        val analysis = analyze(name, text)
        val semanticRanges = analysis.result.getOrNull()?.let { semanticRanges(it.analysis) }.orEmpty()
        val bracketRanges = matchingBracketRanges(text, offset)
        val lineHints = analysis.result.getOrNull()?.let { inlayHints(it.analysis) }.orEmpty()
        return buildHighlightedLines(text, semanticRanges + bracketRanges, lineHints)
    }

    override fun completions(name: String, text: String, offset: Int): List<CompletionItem> {
        val snapshot = analyze(name, text).result.getOrNull()
        val context = CompletionContext.from(text, offset)
        val symbols = snapshot?.symbols ?: editorSymbols
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
            .distinctBy { it.name to it.show to it.tag }
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
                    val bindings = createHollowKatariEditorBindings()
                    val analysis = analyzeKatariNarrativeScript(name, text, bindings)
                    AnalysisSnapshot(analysis, KatariSymbols.from(analysis))
                }
                CachedAnalysis(name, text, result).also { cached = it }
            }
        }
    }
}

    private fun semanticRanges(analysis: KatariNarrativeAnalysis): List<StyledRange> {
        val ranges = mutableListOf<StyledRange>()
        analysis.semanticScript.walk { node, parent ->
            when (node) {
                is PropertyDeclarationNode -> {
                    ranges += StyledRange.identifier(node.position, node.name, TokenType.PROPERTY_IDENTIFIER)
                }
                is FunctionDeclarationNode -> {
                    ranges += StyledRange.identifier(node.position, node.name, TokenType.FUNCTION, bold = true)
                }
                is FunctionValueParameterNode -> {
                    ranges += StyledRange.identifier(node.position, node.name, TokenType.PARAMETER)
                }
                is ValueParameterDeclarationNode -> {
                    ranges += StyledRange.identifier(node.position, node.name, TokenType.PARAMETER)
                }
                is VariableReferenceNode -> {
                    val type = when {
                        parent is FunctionCallNode && parent.function === node -> TokenType.FUNCTION
                        node.variableName.firstOrNull()?.isUpperCase() == true -> TokenType.CLASS
                        else -> TokenType.VARIABLE
                    }
                    ranges += StyledRange.identifier(node.position, node.variableName, type)
                }
                is ClassMemberReferenceNode -> {
                    val token = if (parent is NavigationNode && parent.isCallTarget(analysis.semanticScript)) {
                        TokenType.METHOD
                    } else {
                        TokenType.FIELD
                    }
                    ranges += StyledRange.identifier(node.position, node.name, token)
                }
                is ClassDeclarationNode -> {
                    ranges += StyledRange.identifier(node.position, node.name, TokenType.ENUM, bold = true)
                }
                else -> Unit
            }
        }
        return ranges
    }

    private fun inlayHints(analysis: KatariNarrativeAnalysis): Map<Int, List<InlayHint>> {
        val hintsByLine = linkedMapOf<Int, MutableList<InlayHint>>()
        analysis.semanticScript.walk { node, _ ->
            when (node) {
                is PropertyDeclarationNode -> {
                    if (node.declaredType == null) {
                        val type = node.inferredType?.descriptiveName() ?: return@walk
                        hintsByLine.add(node.position, node.name.length, ": $type")
                    }
                }
                is FunctionValueParameterNode -> {
                    if (node.declaredType == null) {
                        val type = node.inferredType?.descriptiveName() ?: return@walk
                        hintsByLine.add(node.position, node.name.length, ": $type")
                    }
                }
                is ValueParameterDeclarationNode -> {
                    if (node.declaredType == null) {
                        val type = node.inferredType?.descriptiveName() ?: return@walk
                        hintsByLine.add(node.position, node.name.length, ": $type")
                    }
                }
                else -> Unit
            }
        }
        return hintsByLine
    }

    private fun MutableMap<Int, MutableList<InlayHint>>.add(position: SourcePosition, nameLength: Int, text: String) {
        if (position.lineNum <= 0 || position.col <= 0) return
        getOrPut(position.lineNum - 1) { mutableListOf() } += InlayHint(position.col - 1 + nameLength, text)
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
        val ranges = byLine[lineIndex].orEmpty()
            .sortedWith(compareBy<StyledRange> { it.start }.thenBy { -it.end }) +
                lexical.sortedWith(compareBy<StyledRange> { it.start }.thenBy { -it.end })
        TextLine(spansFor(line, ranges), ArrayList(hints[lineIndex].orEmpty()))
    }
    }

    private fun tokenize(line: String): List<StyledRange> {
        val ranges = mutableListOf<StyledRange>()
        var i = 0
        while (i < line.length) {
            val start = i
            when {
                line.startsWith("//", i) -> {
                    ranges += StyledRange(0, i, line.length, style(TokenType.COMMENT))
                    break
                }
                line[i] == '"' || line[i] == '\'' -> {
                    val quote = line[i++]
                    while (i < line.length) {
                        if (line[i] == '\\' && i + 1 < line.length) i += 2
                        else if (line[i++] == quote) break
                    }
                    ranges += StyledRange(0, start, i, style(TokenType.STRING))
                }
                line[i].isDigit() -> {
                    while (i < line.length && (line[i].isDigit() || line[i] == '.')) i++
                    ranges += StyledRange(0, start, i, style(TokenType.NUMERIC_LITERAL))
                }
                line[i].isIdentifierStart() -> {
                    i++
                    while (i < line.length && line[i].isIdentifierPart()) i++
                    val word = line.substring(start, i)
                    if (word in keywords) ranges += StyledRange(0, start, i, style(TokenType.KEYWORD))
                }
                else -> i++
            }
        }
        return ranges
    }

    private fun spansFor(line: String, ranges: List<StyledRange>): List<Pair<String, SpanStyle>> {
        if (line.isEmpty()) return listOf("" to style(TokenType.DEFAULT))
        val styles = Array(line.length) { style(TokenType.DEFAULT) }
        ranges.forEach { range ->
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
                    StyledRange(pos.line, pos.column, pos.column + 1, style(TokenType.DEFAULT, highlight = true, bold = true))
                }
            }
            i += direction
        }
        return emptyList()
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

    private fun style(
        token: TokenType,
        italic: Boolean = false,
        bold: Boolean = false,
        highlight: Boolean = false,
    ) = SpanStyle(token, italic = italic, bold = bold, highlight = highlight)

    private fun Char.isIdentifierStart() = this == '_' || isLetter()
    private fun Char.isIdentifierPart() = this == '_' || isLetterOrDigit()

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
    ) {
        companion object {
            fun identifier(position: SourcePosition, text: String, token: TokenType, bold: Boolean = false): StyledRange {
                return StyledRange(position.lineNum - 1, position.col - 1, position.col - 1 + text.length, style(token, bold = bold))
            }
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
        val parameters: List<ParameterEntry>,
        val returnType: String?,
    ) {
        fun toCompletion(): CompletionItem.Declaration {
            val insertText = if (parameters.isEmpty()) "$name()" else "$name(${parameters.joinToString { it.name + " = " }})"
            return declarationCompletionItem {
                show = this@FunctionEntry.name
                insert = insertText
                this.name = this@FunctionEntry.name
                tag = CompletionItemTag.FUNCTION
                middle = parameters.joinToString(prefix = "(", postfix = ")") { it.display() }
                returnType?.let { tail = it }
                moveCaret = if (parameters.isEmpty()) -1 else -1
            }
        }
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
    ) {
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
                    return CompletionContext(prefix, receiver.takeIf { it.isNotBlank() }, receiverStart)
                }
                return CompletionContext(prefix, null, -1)
            }

            private fun scanIdentifierStart(text: String, offset: Int): Int {
                var index = offset
                while (index > 0 && text[index - 1].isIdentifierPart()) index--
                return index
            }
        }
    }

    private fun KatariNarrativeAnalysis.analysisEnumEntries(): Map<String, List<String>> {
        return enumDefinitions.mapValues { (_, definition) -> definition.entries.map { it.entryName } }
    }

    private fun String.cleanType(): String {
        return removeSuffix("?").substringBefore('<')
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
