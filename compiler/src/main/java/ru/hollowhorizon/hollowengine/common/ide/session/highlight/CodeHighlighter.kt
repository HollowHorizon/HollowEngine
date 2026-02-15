package ru.hollowhorizon.hollowengine.common.ide.session.highlight

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.InlayHint
import ru.hollowhorizon.hollowengine.common.ide.session.inlays.provideHints
import ru.hollowhorizon.hollowengine.common.scripting.ide.SpanStyle
import ru.hollowhorizon.hollowengine.common.scripting.ide.TextLine
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType

class LineBuilder(val lines: MutableList<TextLine>) {

    fun append(builder: SpanBuilder.() -> Unit) {
        lines.add(SpanBuilder(arrayListOf()).apply(builder).build())
    }

    class SpanBuilder(private val spans: MutableList<Pair<String, SpanStyle>>) {
        var tokenType = TokenType.DEFAULT
        var italic = false
        var bold = false
        var highlight = false
        private val hints = ArrayList<InlayHint>()


        fun append(text: String) {
            spans.add(text to SpanStyle(tokenType, italic, bold, highlight))
            italic = false
            bold = false
            highlight = false
        }

        fun appendHint(hint: InlayHint) {
            hints.add(hint)
        }

        fun build() = TextLine(spans, hints)
    }
}

fun highlightCode(file: KtFile, offset: Int): List<TextLine> {
    val code = file.text
    val lineCount = code.lines().size
    val builder = LineBuilder(arrayListOf())

    val safeOffset = offset.coerceIn(0, file.textLength)
    val elementAtCaret = file.findElementAt(safeOffset).takeIfSelectable()
        ?: if (safeOffset > 0) file.findElementAt(safeOffset - 1).takeIfSelectable() else null

    analyze(file) {
        val hints = provideHints(file)

        // Предварительно вычисляем ключи символов под курсором
        val caretKeys = elementAtCaret?.let { element ->
            val expression = element.parentsWithSelf.firstIsInstanceOrNull<KtExpression>()
            val symbols = expression?.let { resolveSymbolsForHighlight(it) } ?: emptyList()
            symbols.map { normalizeToKey(it) }.toSet()
        } ?: emptySet()

        for (i in 0 until lineCount) {
            builder.append {
                for (element in getElementsAtLine(file, i)) {
                    renderPsiElement(element, elementAtCaret, caretKeys)
                }

                val document = file.fileDocument
                val lineStartOffset = document.getLineStartOffset(i)
                val lineEndOffset = document.getLineEndOffset(i)
                hints.filter { it.index in lineStartOffset .. lineEndOffset }.forEach { appendHint(it.copy(index = it.index - lineStartOffset)) }
            }
        }
    }

    return builder.lines
}

private val selectable: List<IElementType> = buildList {
    add(KtTokens.DOT)
    addAll(KtTokens.ALL_ASSIGNMENTS.types)
}

private fun PsiElement?.takeIfSelectable() = takeIf { it !is PsiWhiteSpace && it?.node?.elementType !in selectable }

context(session: KaSession)
private fun LineBuilder.SpanBuilder.renderPsiElement(
    element: Element,
    elementAtCaret: PsiElement?,
    caretKeys: Set<KaSymbol>,
) {
    with(session) {
        val psi = element.psiElement
        val parent = psi.parent
        val text = element.text
        val elementType = psi.node.elementType

        tokenType = TokenType.DEFAULT
        bold = false
        italic = false

        when {
            psi is KtAnnotationEntry -> tokenType = TokenType.ANNOTATION
            elementType in KtTokens.KEYWORDS || elementType in KtTokens.SOFT_KEYWORDS -> tokenType = TokenType.KEYWORD
            elementType in KtTokens.STRINGS || elementType == KtTokens.OPEN_QUOTE || elementType == KtTokens.CLOSING_QUOTE -> tokenType = TokenType.STRING
            KtTokens.COMMENTS.contains(elementType) -> tokenType = TokenType.COMMENT
            parent is KtConstantExpression && !isEnumConstant(parent) -> tokenType = TokenType.NUMERIC_LITERAL

            else -> {
                val symbols = resolveSymbolsForHighlight(parent, psi)

                val bestSymbol = pickBestSymbol(symbols)

                if (bestSymbol != null) {
                    computeSymbolStyle(bestSymbol)
                } else if (parent is KtConstantExpression && isEnumConstant(parent)) {
                    tokenType = TokenType.PROPERTY_IDENTIFIER
                }
            }
        }

        if (shouldHighlight(psi, elementAtCaret, caretKeys)) {
            highlight = true
        }

        append(text)
    }
}

context(session: KaSession)
private fun shouldHighlight(
    current: PsiElement,
    target: PsiElement?,
    caretKeys: Set<KaSymbol>,
): Boolean {
    with(session) {
        if (target == null) return false

        if (current == target) return true

        if (current.isOtherParenthesis(target)) return true
        if (target.isOtherParenthesis(current)) return true

        if (current.node.elementType != target.node.elementType) return false

        if (caretKeys.isEmpty()) return false

        val currentExpression = current.parentsWithSelf.firstIsInstanceOrNull<KtExpression>() ?: return false
        val currentSymbols = resolveSymbolsForHighlight(currentExpression)

        return currentSymbols.any { symbol ->
            normalizeToKey(symbol) in caretKeys
        }
    }
}

/**
 * Главная логика нормализации для сравнения.
 * Возвращает "Ключ", по которому символы считаются эквивалентными.
 */
context(session: KaSession)
private fun normalizeToKey(symbol: KaSymbol): KaSymbol {
    with(session) {
        return when (symbol) {
            // Конструктор -> ID класса
            is KaConstructorSymbol -> symbol.containingSymbol ?: symbol

            // Класс -> ID класса
            is KaClassSymbol -> {
                // Если это Companion Object, считаем его равным самому классу
                if (symbol.classKind == KaClassKind.COMPANION_OBJECT) symbol.containingSymbol ?: symbol
                else symbol
            }

            // Для остальных символов (функции, переменные) ключом является сам символ (equals/hashCode)
            else -> symbol
        }
    }
}

context(session: KaSession)
private fun resolveSymbolsForHighlight(element: PsiElement, specificPsi: PsiElement? = null): List<KaSymbol> {
    with(session) {
        return when (element) {
            is KtSimpleNameExpression -> element.mainReference.resolveToSymbols().toList()
            is KtNamedDeclaration -> {
                // Если указан specificPsi (например, идентификатор имени), проверяем, что это он
                if (specificPsi != null && element.nameIdentifier != specificPsi) return emptyList()
                listOfNotNull(element.symbol)
            }

            else -> emptyList()
        }
    }
}

// --- Остальные методы (Стилизация и утилиты) без изменений или с мелкими правками ---

private fun pickBestSymbol(symbols: Collection<KaSymbol>): KaSymbol? {
    if (symbols.isEmpty()) return null
    if (symbols.size == 1) return symbols.first()

    return symbols.firstOrNull { it is KaClassSymbol }
        ?: symbols.firstOrNull { it is KaConstructorSymbol }
        ?: symbols.firstOrNull { it is KaVariableSymbol }
        ?: symbols.firstOrNull { it is KaFunctionSymbol }
        ?: symbols.first()
}

context(session: KaSession)
private fun LineBuilder.SpanBuilder.computeSymbolStyle(symbol: KaSymbol) {
    with(session) {
        tokenType = when (symbol) {
            is KaPackageSymbol -> TokenType.DEFAULT
            is KaTypeAliasSymbol -> {
                symbol.expandedType.expandedSymbol?.let {
                    computeSymbolStyle(it)
                    tokenType
                } ?: TokenType.CLASS
            }

            is KaTypeParameterSymbol -> TokenType.PARAMETER
            is KaClassSymbol -> when (symbol.classKind) {
                KaClassKind.CLASS -> TokenType.CLASS
                KaClassKind.ENUM_CLASS -> TokenType.ENUM
                KaClassKind.INTERFACE -> TokenType.INTERFACE
                KaClassKind.ANNOTATION_CLASS -> TokenType.ANNOTATION
                KaClassKind.OBJECT, KaClassKind.COMPANION_OBJECT, KaClassKind.ANONYMOUS_OBJECT -> TokenType.OBJECT
            }

            is KaVariableSymbol -> when (symbol) {
                is KaEnumEntrySymbol -> TokenType.PROPERTY_IDENTIFIER
                is KaBackingFieldSymbol, is KaJavaFieldSymbol -> TokenType.FIELD
                is KaLocalVariableSymbol -> TokenType.VARIABLE
                is KaValueParameterSymbol -> TokenType.PARAMETER
                is KaKotlinPropertySymbol, is KaSyntheticJavaPropertySymbol -> TokenType.PROPERTY_IDENTIFIER
                else -> TokenType.VARIABLE
            }

            is KaFunctionSymbol -> when (symbol) {
                is KaConstructorSymbol -> TokenType.CLASS
                is KaNamedFunctionSymbol -> {
                    if (symbol.isExtension) TokenType.EXTENSION_RECEIVER
                    else if (symbol.location == KaSymbolLocation.TOP_LEVEL) TokenType.TOP_LEVEL
                    else if (symbol.location == KaSymbolLocation.CLASS) TokenType.METHOD
                    else TokenType.FUNCTION
                }

                else -> TokenType.FUNCTION
            }

            else -> TokenType.DEFAULT
        }
        applyModifiers(symbol)
    }
}

context(session: KaSession)
private fun LineBuilder.SpanBuilder.applyModifiers(symbol: KaSymbol) {
    with(session) {
        if (symbol is KaCallableSymbol) {
            if (symbol.location == KaSymbolLocation.TOP_LEVEL && (symbol is KaNamedFunctionSymbol && symbol.isStatic)) {
                italic = true
            }
        }
    }
}

fun getElementsAtLine(psiFile: PsiFile, lineNumber: Int): List<Element> {
    val elements = mutableListOf<Element>()
    val document = psiFile.fileDocument

    if (lineNumber < 0 || lineNumber >= document.lineCount) return emptyList()

    val lineStartOffset = document.getLineStartOffset(lineNumber)
    val lineEndOffset = document.getLineEndOffset(lineNumber)
    val lineRange = TextRange(lineStartOffset, lineEndOffset)

    var currentElement: PsiElement? = psiFile.findElementAt(lineStartOffset)
    while (currentElement != null && currentElement.textRange.startOffset < lineEndOffset) {
        val elementRange = currentElement.textRange
        val intersection = elementRange.intersection(lineRange)
        elements.add(Element(currentElement, document.getText(intersection)))
        currentElement = PsiTreeUtil.nextLeaf(currentElement)
    }
    return elements
}

context(session: KaSession)
private fun isEnumConstant(element: KtElement): Boolean {
    with(session) {
        val expression = element as? KtReferenceExpression ?: return false
        val symbol = expression.mainReference.resolveToSymbol()
        return symbol is KaEnumEntrySymbol
    }
}

private fun PsiElement.isOtherParenthesis(other: PsiElement): Boolean {
    val matchingPairs = mapOf(
        KtTokens.LPAR to KtTokens.RPAR,
        KtTokens.LBRACE to KtTokens.RBRACE,
        KtTokens.LBRACKET to KtTokens.RBRACKET
    )
    val thisType = this.node.elementType
    val otherType = other.node.elementType

    if (matchingPairs[thisType] != otherType && matchingPairs[otherType] != thisType) return false
    val commonParent = PsiTreeUtil.findCommonParent(this, other) ?: return false
    return commonParent.firstChild == this && commonParent.lastChild == other ||
            commonParent.firstChild == other && commonParent.lastChild == this
}

class Element(val psiElement: PsiElement, val text: String)