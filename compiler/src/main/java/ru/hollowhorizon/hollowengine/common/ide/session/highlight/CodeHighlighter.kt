package ru.hollowhorizon.hollowengine.common.ide.session.highlight

import com.intellij.lang.ASTNode
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
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import ru.hollowhorizon.hollowengine.common.scripting.ide.SpanStyle
import ru.hollowhorizon.hollowengine.common.scripting.ide.SymbolColor
import ru.hollowhorizon.hollowengine.common.scripting.ide.TextLine

class LineBuilder(val lines: MutableList<TextLine>) {
    fun append(builder: SpanBuilder.() -> Unit) {
        lines.add(SpanBuilder(arrayListOf()).apply(builder).build())
    }

    class SpanBuilder(private val spans: MutableList<Pair<String, SpanStyle>>) {
        var symbolColor = SymbolColor.DEFAULT
        var italic = false
        var bold = false
        var highlight = false

        fun append(text: String) {
            spans.add(text to SpanStyle(symbolColor, italic, bold, highlight))
            italic = false
            bold = false
            highlight = false
        }

        fun build() = TextLine(spans)
    }
}

fun highlightCode(file: KtFile, offset: Int): List<TextLine> {
    val code = file.text
    val lineCount = code.lines().size

    val builder = LineBuilder(arrayListOf())

    val elementAtCaret = file.findElementAt(offset).takeIfSelectable()
        ?: file.findElementAt(offset - 1).takeIfSelectable()

    analyze(file) {
        for (i in 0 until lineCount) {
            builder.append {
                for (element in getElementsAtLine(file, i)) {
                    renderPsiElement(element, elementAtCaret, offset)
                }
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
private fun LineBuilder.SpanBuilder.renderPsiElement(element: Element, elementAtCaret: PsiElement?, offset: Int) {
    val psi = element.psiElement
    val parent = psi.parent
    val text = element.text

    val elementType = psi.node.elementType

    when {
        psi is KtAnnotationEntry -> symbolColor = SymbolColor.ANNOTATION

        KtTokens.KEYWORDS.contains(elementType) || KtTokens.SOFT_KEYWORDS.contains(elementType) -> {
            symbolColor = SymbolColor.KEYWORD
        }

        elementType in KtTokens.STRINGS ||
                elementType == KtTokens.OPEN_QUOTE ||
                elementType == KtTokens.CLOSING_QUOTE -> {
            symbolColor = SymbolColor.STRING
        }

        parent is KtConstantExpression -> {
            symbolColor = if (isEnumConstant(psi)) SymbolColor.PROPERTY_IDENTIFIER else SymbolColor.NUMERIC_LITERAL
        }

        KtTokens.COMMENTS.contains(elementType) -> symbolColor = SymbolColor.COMMENT

        elementType == KtTokens.SHORT_TEMPLATE_ENTRY_START ||
                elementType == KtTokens.LONG_TEMPLATE_ENTRY_START ||
                elementType == KtTokens.LONG_TEMPLATE_ENTRY_END -> {
            symbolColor = SymbolColor.NAME_REFERENCE
        }

        parent is KtReferenceExpression -> {
            parent.computeStyle()
        }

        else -> symbolColor = SymbolColor.DEFAULT
    }

    if (psi.shouldHighlight(elementAtCaret)) {
        highlight = true
    }

    append(text)
}

context(builder: LineBuilder.SpanBuilder)
private fun KtReferenceExpression.computeStyle() {
    analyze(this) {
        when (val symbol = mainReference.resolveToSymbol()) {
            is KaNamedFunctionSymbol -> {
                if (symbol.isExtension) builder.symbolColor = SymbolColor.EXTENSION_RECEIVER
                else if (symbol.location == KaSymbolLocation.TOP_LEVEL) builder.symbolColor = SymbolColor.TOP_LEVEL
            }

            is KaValueParameterSymbol -> builder.symbolColor = SymbolColor.VALUE_ARGUMENT_NAME
            is KaEnumEntrySymbol -> builder.symbolColor = SymbolColor.PROPERTY_IDENTIFIER
            is KaVariableSymbol -> builder.symbolColor = SymbolColor.PROPERTY_IDENTIFIER
            null -> builder.symbolColor = SymbolColor.NAME_REFERENCE
        }
    }
}

fun getElementsAtLine(psiFile: PsiFile, lineNumber: Int): List<Element> {
    val elements = mutableListOf<Element>()

    val document = psiFile.fileDocument

    if (lineNumber < 0 || lineNumber >= document.lineCount) {
        return emptyList()
    }

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

private fun isEnumConstant(psi: PsiElement): Boolean {
    if (psi !is KtElement) return false
    val symbol = analyze(psi) { (psi as? KtReferenceExpression)?.mainReference?.resolveToSymbol() }
    return symbol is KaEnumEntrySymbol
}

operator fun ASTNode.contains(other: PsiElement): Boolean {
    val stack = ArrayDeque<ASTNode>()
    stack.add(this)

    while (stack.isNotEmpty()) {
        val currentNode = stack.removeLast()
        if (currentNode == other.node) {
            return true
        }
        currentNode.children().forEach { stack.add(it) }
    }

    return false
}

fun PsiElement.shouldHighlight(other: PsiElement?): Boolean {
    if (other == null) return false

    val otherType = other.node.elementType
    when (otherType) {
        in KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET -> return false
        KtTokens.LPAR, KtTokens.RPAR, KtTokens.LBRACE, KtTokens.RBRACE, KtTokens.LBRACKET, KtTokens.RBRACKET ->
            return this == other || isOtherParenthesis(other)

        KtTokens.CLOSING_QUOTE, KtTokens.OPEN_QUOTE -> return this in other.parent.node
    }

    if (this == other) return true

    val selfExpression = this.parentsWithSelf.firstIsInstanceOrNull<KtExpression>() ?: return false
    val otherExpression = other.parentsWithSelf.firstIsInstanceOrNull<KtExpression>() ?: return false

    return analyze(selfExpression) {
        val symbol1 = when (selfExpression) {
            is KtReferenceExpression -> selfExpression.mainReference.resolveToSymbol()
            is KtNamedDeclaration -> selfExpression.symbol
            else -> null
        }
        val symbol2 = when (otherExpression) {
            is KtReferenceExpression -> otherExpression.mainReference.resolveToSymbol()
            is KtNamedDeclaration -> otherExpression.symbol
            else -> null
        }

        fun KaSymbol.normalize(): KaSymbol {
            if (this is KaConstructorSymbol) {
                // Получаем ID класса, к которому принадлежит конструктор
                val classId = this.containingClassId
                // Ищем символ этого класса. Если нашли — возвращаем его, иначе оставляем как есть
                if (classId != null) {
                    return findClass(classId) ?: this
                }
            }
            return this
        }

        val s1 = symbol1?.normalize()
        val s2 = symbol2?.normalize()

        s1 != null && s1 == s2 && node.elementType == other.node.elementType
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

    if (matchingPairs[thisType] != otherType && matchingPairs[otherType] != thisType) {
        return false
    }

    val commonParent = PsiTreeUtil.findCommonParent(this, other) ?: return false

    return commonParent.firstChild == this && commonParent.lastChild == other ||
            commonParent.firstChild == other && commonParent.lastChild == this
}

class Element(val psiElement: PsiElement, val text: String)