package ru.hollowhorizon.hollowengine.common.ide.session.insight

import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.resolveToCallCandidates
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaConstructorSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaDeclarationSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaVariableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.types.Variance
import ru.hollowhorizon.hollowengine.common.ide.session.ScriptingAnalyzerImpl
import ru.hollowhorizon.hollowengine.common.scripting.ide.CallableSignature
import ru.hollowhorizon.hollowengine.common.scripting.ide.CodeInsightHighlight
import ru.hollowhorizon.hollowengine.common.scripting.ide.HoverInfo
import ru.hollowhorizon.hollowengine.common.scripting.ide.OccurrenceRange
import ru.hollowhorizon.hollowengine.common.scripting.ide.SignatureHelp
import ru.hollowhorizon.hollowengine.common.scripting.ide.TokenType

fun ScriptingAnalyzerImpl.findSignatureHelp(file: KtFile, offset: Int): SignatureHelp? {
    val call = file.callAt(offset) ?: return null
    val argumentList = call.valueArgumentList ?: return null
    return analyze(file) {
        val candidates = call.resolveToCallCandidates()
            .mapNotNull { candidateInfo ->
                val functionCall = candidateInfo.candidate as? KaFunctionCall<*> ?: return@mapNotNull null
                renderSignature(functionCall.resolvedSignature()) to candidateInfo.isInBestCandidates
            }
            .distinctBy { (signature) -> signature.label }
        val signatures = candidates.map { (signature) -> signature }
        if (signatures.isEmpty()) return@analyze null
        SignatureHelp(
            anchor = argumentList.leftParenthesis?.textRange?.endOffset ?: argumentList.textRange.startOffset,
            signatures = signatures,
            activeSignature = candidates.indexOfFirst { (_, isBest) -> isBest }.coerceAtLeast(0),
            activeParameter = argumentList.activeParameter(offset),
        )
    }
}

fun ScriptingAnalyzerImpl.findHoverInfo(file: KtFile, offset: Int): HoverInfo? {
    val expression = file.referenceAt(offset) ?: return null
    return analyze(file) {
        val symbol = expression.mainReference.resolveToSymbols().firstNotNullOfOrNull { it as? KaDeclarationSymbol }
            ?: return@analyze null
        val presentation = renderSymbol(symbol)
        HoverInfo(
            start = expression.textRange.startOffset,
            end = expression.textRange.endOffset,
            signature = presentation.text,
            documentation = symbol.documentationText(),
            highlights = presentation.highlights,
        )
    }
}

private fun KtFile.callAt(offset: Int): KtCallExpression? {
    if (textLength == 0) return null
    val safeOffset = offset.coerceIn(0, textLength)
    return sequenceOf(safeOffset, safeOffset - 1)
        .filter { it in 0 until textLength }
        .mapNotNull(::findElementAt)
        .flatMap(PsiElement::parentsWithSelf)
        .filterIsInstance<KtCallExpression>()
        .firstOrNull { call -> call.valueArgumentList?.textRange?.containsOffset(safeOffset) == true }
}

private fun KtFile.referenceAt(offset: Int): KtSimpleNameExpression? {
    if (textLength == 0) return null
    val safeOffset = offset.coerceIn(0, textLength)
    return sequenceOf(safeOffset, safeOffset - 1)
        .filter { it in 0 until textLength }
        .mapNotNull(::findElementAt)
        .flatMap(PsiElement::parentsWithSelf)
        .filterIsInstance<KtSimpleNameExpression>()
        .firstOrNull()
}

private fun KtValueArgumentList.activeParameter(offset: Int): Int {
    arguments.indexOfFirst { offset <= it.textRange.endOffset }.takeIf { it >= 0 }?.let { return it }
    return arguments.count { it.textRange.endOffset < offset }
}

context(session: KaSession)
@OptIn(KaExperimentalApi::class)
private fun renderSignature(signature: KaFunctionSignature<*>): CallableSignature = with(session) {
    val symbol = signature.symbol
    val name = when (symbol) {
        is KaConstructorSymbol -> symbol.containingSymbol?.name?.asString() ?: "constructor"
        else -> symbol.name?.asString() ?: "invoke"
    }
    val parameters = ArrayList<OccurrenceRange>(signature.valueParameters.size)
    val highlights = ArrayList<CodeInsightHighlight>(signature.valueParameters.size * 3 + 2)
    var presentationStart = 0
    val label = buildString {
        appendHighlighted(
            name,
            when {
                symbol is KaConstructorSymbol -> TokenType.CLASS
                symbol.containingSymbol is KaClassLikeSymbol -> TokenType.METHOD
                else -> TokenType.FUNCTION
            },
            highlights,
        )
        presentationStart = length
        append('(')
        signature.valueParameters.forEachIndexed { index, parameter ->
            if (index > 0) append(", ")
            val start = length
            if (parameter.symbol.isVararg) {
                appendHighlighted("vararg", TokenType.KEYWORD, highlights)
                append(' ')
            }
            appendHighlighted(parameter.name.asString(), TokenType.VALUE_ARGUMENT_NAME, highlights)
            append(": ")
            appendHighlighted(
                parameter.returnType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.IN_VARIANCE),
                TokenType.CLASS,
                highlights,
            )
            if (parameter.symbol.hasDefaultValue) {
                append(" = ")
                appendHighlighted("...", TokenType.DEFAULT, highlights)
            }
            parameters += OccurrenceRange(start, length)
        }
        append(')')
        if (symbol !is KaConstructorSymbol) {
            append(": ")
            appendHighlighted(
                signature.returnType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.OUT_VARIANCE),
                TokenType.CLASS,
                highlights,
            )
        }
    }
    CallableSignature(
        label = label,
        parameters = parameters,
        documentation = symbol.documentationText(),
        highlights = highlights,
        presentation = OccurrenceRange(presentationStart, label.length),
    )
}

context(session: KaSession)
private fun renderSymbol(symbol: KaDeclarationSymbol): RenderedSymbol = with(session) {
    when (symbol) {
        is KaConstructorSymbol -> renderSignature(symbol.asSignature()).toRenderedSymbol()
        is KaFunctionSymbol -> renderSignature(symbol.asSignature()).toRenderedSymbol()
        is KaVariableSymbol -> {
            val highlights = ArrayList<CodeInsightHighlight>(3)
            val text = buildString {
                appendHighlighted(
                    if (symbol is KaPropertySymbol && symbol.isVal) "val" else "var",
                    TokenType.KEYWORD,
                    highlights,
                )
                append(' ')
                appendHighlighted(symbol.name.asString(), TokenType.VARIABLE, highlights)
                append(": ")
                appendHighlighted(
                    symbol.returnType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.OUT_VARIANCE),
                    TokenType.CLASS,
                    highlights,
                )
            }
            RenderedSymbol(text, highlights)
        }

        is KaClassLikeSymbol -> {
            val highlights = ArrayList<CodeInsightHighlight>(2)
            val text = buildString {
                appendHighlighted("class", TokenType.KEYWORD, highlights)
                append(' ')
                appendHighlighted(symbol.name?.asString() ?: "<anonymous>", TokenType.CLASS, highlights)
            }
            RenderedSymbol(text, highlights)
        }

        else -> RenderedSymbol(symbol.name?.asString() ?: symbol::class.simpleName.orEmpty())
    }
}

private fun StringBuilder.appendHighlighted(
    value: String,
    tokenType: TokenType,
    highlights: MutableList<CodeInsightHighlight>,
) {
    val start = length
    append(value)
    if (start < length) {
        highlights += CodeInsightHighlight(OccurrenceRange(start, length), tokenType)
    }
}

private fun CallableSignature.toRenderedSymbol() = RenderedSymbol(label, highlights)

private data class RenderedSymbol(
    val text: String,
    val highlights: List<CodeInsightHighlight> = emptyList(),
)

@Suppress("DEPRECATION")
private fun KaFunctionCall<*>.resolvedSignature(): KaFunctionSignature<*> = partiallyAppliedSymbol.signature

private fun PsiElement?.documentationText(): String? {
    val owner = this ?: return null
    val raw = when (owner) {
        is KtDeclaration -> owner.docComment?.text
        is PsiDocCommentOwner -> owner.docComment?.text
        else -> null
    } ?: when (val navigation = owner.navigationElement) {
        is KtDeclaration -> navigation.docComment?.text
        is PsiDocCommentOwner -> navigation.docComment?.text
        else -> null
    }
    return raw?.lineSequence()
        ?.map { line -> line.trim().removePrefix("/**").removeSuffix("*/").removePrefix("*").trim() }
        ?.dropWhile(String::isBlank)
        ?.toList()
        ?.dropLastWhile(String::isBlank)
        ?.joinToString("\n")
        ?.takeIf(String::isNotBlank)
}

context(session: KaSession)
private fun KaDeclarationSymbol.documentationText(): String? = with(session) {
    psi.documentationText() ?: (this@documentationText as? KaConstructorSymbol)
        ?.containingSymbol
        ?.psi
        .documentationText()
}
