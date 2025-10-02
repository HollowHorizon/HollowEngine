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
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.children
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.stubs.elements.KtNameReferenceExpressionElementType
import org.jetbrains.kotlin.psi.stubs.elements.KtPropertyElementType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.descriptorUtil.parentsWithSelf
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.IdeTheme
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.post
import ru.hollowhorizon.hollowengine.common.project.kt.imports.UNUSED_IMPORT_FACTORY

private data class UnusedInfo(
    val unusedVariables: Set<PsiElement>,
    val unusedParameters: Set<PsiElement>,
    val unusedExpressions: Set<PsiElement>,
    val unusedImports: Set<PsiElement>,
)

private fun collectUnusedElements(bindingContext: BindingContext): UnusedInfo {
    val diagnostics = bindingContext.diagnostics
    return UnusedInfo(
        unusedVariables = diagnostics
            .filter { it.factory == Errors.UNUSED_VARIABLE }
            .mapTo(HashSet()) { it.psiElement },
        unusedParameters = diagnostics
            .filter { it.factory == Errors.UNUSED_PARAMETER }
            .mapTo(HashSet()) { it.psiElement },
        unusedExpressions = diagnostics
            .filter { it.factory == Errors.UNUSED_EXPRESSION }
            .mapTo(HashSet()) { it.psiElement },
        unusedImports = diagnostics
            .filter { it.factory == UNUSED_IMPORT_FACTORY }
            .mapTo(HashSet()) { it.psiElement }
    )
}

object ScriptColorizer {
    fun colorize(
        file: KtFile,
        font: MsdfFont,
        bindingContext: BindingContext,
        expressionAtCaret: PsiElement?,
    ): List<TextLine> {
        if (!KoolSystem.isInitialized) return emptyList()
        val textLines = mutableListOf<TextLine>()
        val currentLine = mutableListOf<Pair<String, TextAttributes>>()
        val unusedInfo = collectUnusedElements(bindingContext)

        file.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)

                if (!element.allChildren.isEmpty || element.text.isEmpty()) return

                var primary = getElementColor(element, bindingContext, unusedInfo)
                val isUnused = element.isUnused(unusedInfo)

                if (isUnused) primary = primary.mix(SyntaxHighlight.COMMENT.mulRgb(0.55f), 0.85f)

                val background = if (element.shouldHighlight(bindingContext, expressionAtCaret)) {
                    IdeTheme.colors.background.mix(primary, 0.35f)
                } else null

                val lines = element.text.split("\n")
                lines.forEachIndexed { index, line ->
                    currentLine.add(line to TextAttributes(font, primary, background))
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

fun PsiElement.shouldHighlight(bindingContext: BindingContext, other: PsiElement?): Boolean {
    if (other == null) return false

    val otherType = other.node.elementType
    when (otherType) {
        in KtTokens.WHITE_SPACE_OR_COMMENT_BIT_SET -> return false
        KtTokens.LPAR, KtTokens.RPAR, KtTokens.LBRACE, KtTokens.RBRACE, KtTokens.LBRACKET, KtTokens.RBRACKET -> return this == other || isOtherParenthesis(
            other
        )

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

private operator fun Set<PsiElement>.contains(other: PsiElement?) = other?.let { contains(it) } == true

private fun PsiElement.isUnused(unusedInfo: UnusedInfo): Boolean {
    val expression = parentsWithSelf.firstIsInstanceOrNull<KtExpression>()
    val declaration = parentsWithSelf.firstIsInstanceOrNull<KtNamedDeclaration>()
    val import = parentsWithSelf.firstIsInstanceOrNull<KtImportDirective>()
    val token = node.elementType

    return when {
        (expression in unusedInfo.unusedVariables
                || expression in unusedInfo.unusedExpressions
                || declaration in unusedInfo.unusedParameters)
                && token == KtTokens.IDENTIFIER && expression !is KtReferenceExpression -> true

        import in unusedInfo.unusedImports -> true

        else -> false
    }
}

private fun getElementColor(element: PsiElement, bindingContext: BindingContext, unusedInfo: UnusedInfo): Color {
    val expression = element.parentsWithSelf.firstIsInstanceOrNull<KtExpression>()
    val token = element.node.elementType

    return when {
        KtTokens.COMMENTS.contains(token) -> SyntaxHighlight.COMMENT
        KtTokens.KEYWORDS.contains(token) || KtTokens.SOFT_KEYWORDS.contains(token) -> SyntaxHighlight.KEYWORD
        KtTokens.STRINGS.contains(token) || token == KtTokens.OPEN_QUOTE || token == KtTokens.CLOSING_QUOTE -> SyntaxHighlight.STRING
        token in setOf(
            KtTokens.LPAR,
            KtTokens.RPAR,
            KtTokens.LBRACE,
            KtTokens.RBRACE,
            KtTokens.DOT
        ) -> SyntaxHighlight.NAME_REFERENCE
        expression?.hasProperty(bindingContext) == true || expression?.isEnumEntry(bindingContext) == true -> SyntaxHighlight.PROPERTY_IDENTIFIER

        (expression as? KtReferenceExpression)?.let {
            val descriptor = bindingContext[BindingContext.REFERENCE_TARGET, it]
            when (descriptor) {
                is ClassDescriptor -> return@let descriptor
                is ConstructorDescriptor -> return@let descriptor.constructedClass
                else -> return@let null
            }
        }
            ?.kind == ClassKind.ANNOTATION_CLASS || element.node.elementType == KtTokens.AT -> SyntaxHighlight.ANNOTATION

        expression.getResolvedCall(bindingContext)?.resultingDescriptor?.parentsWithSelf?.any {
            it.annotations.hasAnnotation(FqName("ru.hollowhorizon.hollowengine.common.graph.GraphDSL"))
        } == true -> SyntaxHighlight.GRAPH

        (expression.getResolvedCall(bindingContext)
            ?.resultingDescriptor?.extensionReceiverParameter != null || expression is KtFunction) -> SyntaxHighlight.EXTENSION_RECEIVER


        expression?.parent is KtValueArgumentName -> SyntaxHighlight.VALUE_ARGUMENT_NAME
        element.isNameReference() || expression is KtNamedDeclaration -> SyntaxHighlight.NAME_REFERENCE
        element.isNumericLiteral() -> SyntaxHighlight.NUMERIC_LITERAL
        element is PsiErrorElement -> SyntaxHighlight.ERROR_ELEMENT
        else -> SyntaxHighlight.DEFAULT
    }
}

fun KtExpression.isEnumEntry(context: BindingContext): Boolean {
    val descriptor = context[BindingContext.REFERENCE_TARGET, this as? KtReferenceExpression ?: return false]
    return descriptor is ClassDescriptor && descriptor.kind == ClassKind.ENUM_ENTRY
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
    val GRAPH = Color("0DA19E")
    val DEFAULT = Color.WHITE
}

private fun KtExpression.hasProperty(bindingContext: BindingContext): Boolean {
    val isNonLocalProperty = (this as? KtProperty)?.isLocal == false
    if (isNonLocalProperty) return true
    return ((this as? KtReferenceExpression)?.let {
        bindingContext[BindingContext.REFERENCE_TARGET, it].let {
            it as? PropertyDescriptor ?: it as? VariableDescriptor
        }
    }?.visibility ?: return false) != DescriptorVisibilities.LOCAL
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