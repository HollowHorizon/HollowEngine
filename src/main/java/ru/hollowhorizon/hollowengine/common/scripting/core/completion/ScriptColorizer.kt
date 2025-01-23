package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import de.fabmax.kool.KoolSystem
import de.fabmax.kool.modules.ui2.TextAttributes
import de.fabmax.kool.modules.ui2.TextLine
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MsdfFont
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.PsiRecursiveElementWalkingVisitor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.allChildren
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

object ScriptColorizer {
    fun colorize(file: KtFile, bindingContext: BindingContext, caretPositionOffset: Int) {
        if (!KoolSystem.isInitialized) return
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

                val attributes = getElementColor(element, bindingContext)

                val lines = element.text.split("\n")
                lines.forEachIndexed { index, line ->
                    currentLine.add(line to TextAttributes(MsdfFont(HACK_FONT, 30f), attributes))
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
    }
}

private fun getElementColor(element: PsiElement, bindingContext: BindingContext): Color {
    val expression = element.parentsWithSelf.firstIsInstanceOrNull<KtExpression>()

    val token = element.node.elementType
    return when {
        KtTokens.KEYWORDS.contains(token) || KtTokens.SOFT_KEYWORDS.contains(token) -> Color("CF8E6D")
        KtTokens.STRINGS.contains(token) || token == KtTokens.OPEN_QUOTE || token == KtTokens.CLOSING_QUOTE -> Color("6AAB73")
        element.isPropertyIdentifier() || expression?.hasProperty(bindingContext) == true -> Color("C77DBB")
        (expression as? KtReferenceExpression)?.getResolvedCall(bindingContext)
            ?.resultingDescriptor?.extensionReceiverParameter != null && token != KtTokens.LPAR && token != KtTokens.RPAR -> Color(
            "56A8F5"
        )

        (expression as? KtReferenceExpression)?.getResolvedCall(bindingContext)
            ?.call?.callElement is KtAnnotationEntry || element.node.elementType == KtTokens.AT -> Color("B3AE60")

        element.isNameReference() -> Color("BCBEC4")
        element.isNumericLiteral() -> Color("2AACB8")
        element is PsiErrorElement -> Color("F75464")
        else -> Color.WHITE
    }
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