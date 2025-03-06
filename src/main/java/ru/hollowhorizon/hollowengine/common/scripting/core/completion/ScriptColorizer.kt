package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import de.fabmax.kool.KoolSystem
import de.fabmax.kool.modules.ui2.TextAttributes
import de.fabmax.kool.modules.ui2.TextLine
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.com.intellij.lang.ASTNode
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.stubs.elements.KtNameReferenceExpressionElementType
import org.jetbrains.kotlin.psi.stubs.elements.KtPropertyElementType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hollowengine.client.gui.scripting.HACK_FONT
import ru.hollowhorizon.hollowengine.client.gui.scripting.ideColors
import ru.hollowhorizon.hollowengine.common.scripting.core.parser.ScriptParser

object ScriptColorizer {
    fun colorize(file: KtFile, bindingContext: BindingContext, expressionAtCaret: PsiElement?): List<TextLine> {
        if (!KoolSystem.isInitialized) return emptyList()
        val textLines = mutableListOf<TextLine>()
        val currentLine = mutableListOf<Pair<String, TextAttributes>>()

        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                // TODO: Нужно что-то сделать с этими параметрами, потому что при их удалении слетает к чертам каретка
                /*                if (element is KtValueArgument) {
                                    // Если в качестве параметра передаётся не примитивный тип, то подсказка к нему не нужна
                                    if (element.children.any { it is KtCallExpression }) return

                                    val callExpression = element.parent.parent as KtCallExpression
                                    val call = callExpression.getResolvedCall(bindingContext)
                                    if (call != null && call.resultingDescriptor.varargParameterPosition() == -1) {
                                        val parameter = call.valueArguments.entries
                                            .firstOrNull { (_, args) -> args.arguments.contains(element) }
                                            ?.key?.name?.asString()

                                        if (parameter != null) currentLine.add(
                                            "$parameter: " to TextAttributes(
                                                MsdfFont(HACK_FONT, 25f),
                                                Color.WHITE,
                                                ideColors.backgroundMid
                                            )
                                        )

                                    }
                                }*/

                if (!element.allChildren.isEmpty || element.text.isEmpty()) return

                val primary = getElementColor(element, bindingContext)

                val background = if (element.shouldHighlight(bindingContext, expressionAtCaret)) {
                    ideColors.background.mix(primary, 0.35f)
                } else null

                val lines = element.text.split("\n")
                lines.forEachIndexed { index, line ->
                    currentLine.add(line to TextAttributes(MsdfFont(HACK_FONT, 10f), primary, background))
                    // Если это конец строки, добавляем в `textLines`
                    if (index != lines.size - 1) {
                        textLines.add(TextLine(currentLine.toList()))
                        currentLine.clear()
                    }
                }
            }
        })

        if (currentLine.isNotEmpty()) textLines.add(TextLine(currentLine))

        OnColorizedEvent(file.name, textLines, file.text.hashCode()).post()

        return textLines
    }

    fun parse(file: String, text: String): List<TextLine> {
        val script = ScriptParser.parse(text, file)
        val (result) = ResolveUtils.analyzeFileForJvm(ScriptParser.env, listOf(script), ScriptParser.env.project)
        return colorize(script, result.bindingContext, null)
    }
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

private fun PsiElement.shouldHighlight(bindingContext: BindingContext, other: PsiElement?): Boolean {
    if (other == null) return false

    val otherType = other.node.elementType
    when (otherType) {
        in KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET -> return false
        KtTokens.LPAR -> return this == other || (other.parent as? KtValueArgumentList)?.rightParenthesis == this
        KtTokens.RPAR -> return this == other || (other.parent as? KtValueArgumentList)?.leftParenthesis == this
        KtTokens.CLOSING_QUOTE, KtTokens.OPEN_QUOTE -> return this in other.parent.node
        else -> {
            if (this == other) return true

            val selfExpression = this.parentsWithSelf.firstIsInstanceOrNull<KtExpression>() ?: return false
            val otherExpression = other.parentsWithSelf.firstIsInstanceOrNull<KtExpression>() ?: return false

            // Функция для получения дескриптора элемента
            fun getElementDescriptor(element: PsiElement): DeclarationDescriptor? {
                return when (element) {
                    is KtReferenceExpression -> bindingContext[BindingContext.REFERENCE_TARGET, element]
                    is KtNamedDeclaration -> bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, element]
                    else -> null
                }
            }

            val descriptor1 = getElementDescriptor(selfExpression)
            val descriptor2 = getElementDescriptor(otherExpression)
            return descriptor1 != null && descriptor1 == descriptor2 && node.elementType == other.node.elementType
        }
    }
}

private fun getElementColor(element: PsiElement, bindingContext: BindingContext): Color {
    val expression = element.parentsWithSelf.firstIsInstanceOrNull<KtExpression>()

    val token = element.node.elementType
    return when {
        KtTokens.COMMENTS.contains(token) -> SyntaxHighlight.COMMENT
        KtTokens.KEYWORDS.contains(token) || KtTokens.SOFT_KEYWORDS.contains(token) -> SyntaxHighlight.KEYWORD
        KtTokens.STRINGS.contains(token) || token == KtTokens.OPEN_QUOTE || token == KtTokens.CLOSING_QUOTE -> SyntaxHighlight.STRING
        element.isPropertyIdentifier() || expression?.hasProperty(bindingContext) == true -> SyntaxHighlight.PROPERTY_IDENTIFIER
        (expression as? KtReferenceExpression)?.getResolvedCall(bindingContext)
            ?.resultingDescriptor?.extensionReceiverParameter != null && token != KtTokens.LPAR && token != KtTokens.RPAR -> SyntaxHighlight.EXTENSION_RECEIVER

        (expression as? KtReferenceExpression)?.getResolvedCall(bindingContext)
            ?.call?.callElement is KtAnnotationEntry || element.node.elementType == KtTokens.AT -> SyntaxHighlight.ANNOTATION

        expression?.parent is KtValueArgumentName -> SyntaxHighlight.VALUE_ARGUMENT_NAME

        element.isNameReference() -> SyntaxHighlight.NAME_REFERENCE
        element.isNumericLiteral() -> SyntaxHighlight.NUMERIC_LITERAL
        element is PsiErrorElement -> SyntaxHighlight.ERROR_ELEMENT
        else -> SyntaxHighlight.DEFAULT
    }
}
object SyntaxHighlight {
    val COMMENT = Color.LIGHT_GRAY
    val KEYWORD = Color("CF8E6D")
    val STRING = Color("6AAB73")
    val PROPERTY_IDENTIFIER = Color("C77DBB")
    val EXTENSION_RECEIVER = Color("56A8F5")
    val ANNOTATION = Color("B3AE60")
    val VALUE_ARGUMENT_NAME = Color("57AAF7")
    val NAME_REFERENCE = Color("BCBEC4")
    val NUMERIC_LITERAL = Color("2AACB8")
    val ERROR_ELEMENT = Color("F75464")
    val DEFAULT = Color.WHITE
}

private fun KtExpression.hasProperty(bindingContext: BindingContext): Boolean {
    return (this as? KtReferenceExpression)?.let { bindingContext[BindingContext.REFERENCE_TARGET, it] is PropertyDescriptor } == true
}

private fun PsiElement.isPropertyIdentifier(): Boolean {
    return this.node.parents().firstOrNull()?.elementType is KtPropertyElementType &&
            this.node.elementType == KtTokens.IDENTIFIER
}

private fun PsiElement.isNameReference(): Boolean {
    return this.node.parents().firstOrNull()?.elementType is KtNameReferenceExpressionElementType
}

private fun PsiElement.isOperationReference(): Boolean {
    return this.node.parents().firstOrNull()?.elementType == KtNodeTypes.OPERATION_REFERENCE
}

private fun PsiElement.isNumericLiteral(): Boolean {
    return this.node.elementType.index in (KtTokens.INTEGER_LITERAL.index..KtTokens.FLOAT_LITERAL.index)
}

class OnColorizedEvent(val fileName: String, val text: List<TextLine>, val hashCode: Int) : Event