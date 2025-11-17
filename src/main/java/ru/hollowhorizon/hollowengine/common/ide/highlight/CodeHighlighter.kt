package ru.hollowhorizon.hollowengine.common.ide.highlight

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import de.fabmax.kool.modules.ui2.TextAttributes
import de.fabmax.kool.modules.ui2.TextLine
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaBackingFieldSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.ide.structure.KaEngineScriptModule

class LineBuilder(val font: MsdfFont, val lines: MutableList<TextLine>) {
    fun append(builder: SpanBuilder.() -> Unit) {
        lines.add(SpanBuilder(font, arrayListOf()).apply(builder).build())
    }

    class SpanBuilder(private val font: MsdfFont, private val spans: MutableList<Pair<String, TextAttributes>>) {
        var currentStyle = TextAttributes(font, HighlightTheme.DEFAULT, null)

        fun append(text: String, color: Color) {
            currentStyle = TextAttributes(font, color, null)
            spans.add(text to currentStyle)
        }

        fun build() = TextLine(spans)
    }
}

fun highlightCode(font: MsdfFont, name: String, code: String, offset: Int): List<TextLine> {
    val structure = HollowEngine.projectStructure
    val file = structure.factory.createFile(name, code)
    val module = KaEngineScriptModule(file, structure.project, buildList {
        addAll(structure.essentialLibraries.kaModules)
        add(structure.builtins.kaModule)
    })
    structure.projectStructureProvider.setModule(file, module)

    val lineCount = code.lines().size

    val builder = LineBuilder(font, arrayListOf())

    analyze(file) {
        for (i in 0 until lineCount) {
            builder.append {
                for (element in getElementsAtLine(file, i)) {
                    renderPsiElement(element)
                }
            }
        }
    }

    return builder.lines
}

private fun LineBuilder.SpanBuilder.renderPsiElement(element: Element) {
    val psi = element.psiElement
    val parent = psi.parent
    val text = element.text

    val elementType = psi.node.elementType
    when {
        KtTokens.KEYWORDS.contains(elementType) || KtTokens.SOFT_KEYWORDS.contains(elementType) -> {
            append(text, HighlightTheme.KEYWORD)
        }

        elementType in KtTokens.STRINGS ||
                elementType == KtTokens.OPEN_QUOTE ||
                elementType == KtTokens.CLOSING_QUOTE -> {
            append(text, HighlightTheme.STRING)
        }

        parent is KtConstantExpression -> {
            append(text, HighlightTheme.NUMERIC_LITERAL)
        }

        KtTokens.COMMENTS.contains(elementType) -> {
            append(text, HighlightTheme.COMMENT)
        }

        elementType == KtTokens.SHORT_TEMPLATE_ENTRY_START ||
                elementType == KtTokens.LONG_TEMPLATE_ENTRY_START ||
                elementType == KtTokens.LONG_TEMPLATE_ENTRY_END -> {
            append(text, HighlightTheme.DEFAULT)
        }

        parent is KtReferenceExpression -> {
            append(text, parent.computeStyle(currentStyle).color)
        }

        else -> append(text, HighlightTheme.DEFAULT)
    }
}

context(LineBuilder.SpanBuilder)
private fun KtReferenceExpression.computeStyle(parentStyle: TextAttributes): TextAttributes =
    analyze(this) {

        val color: Color? = when (val symbol = mainReference.resolveToSymbol()) {
            is KaNamedFunctionSymbol -> {
                if (symbol.isExtension) HighlightTheme.EXTENSION_RECEIVER
                else if (symbol.location == KaSymbolLocation.TOP_LEVEL) HighlightTheme.TOP_LEVEL
                else null
            }

            is KaBackingFieldSymbol -> HighlightTheme.PROPERTY_IDENTIFIER
            is KaPropertySymbol -> HighlightTheme.PROPERTY_IDENTIFIER
            else -> null
        }

        color?.let { TextAttributes(parentStyle.font, it, null) }
    } ?: parentStyle

private fun getElementsAtLine(psiFile: PsiFile, lineNumber: Int): List<Element> {
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

private class Element(val psiElement: PsiElement, val text: String)