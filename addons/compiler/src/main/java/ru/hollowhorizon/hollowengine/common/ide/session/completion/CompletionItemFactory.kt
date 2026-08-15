package ru.hollowhorizon.hollowengine.common.ide.session.completion

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.signatures.KaCallableSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaFunctionSignature
import org.jetbrains.kotlin.analysis.api.signatures.KaVariableSignature
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.builtins.functions.FunctionTypeKind
import org.jetbrains.kotlin.types.Variance
import ru.hollowhorizon.hollowengine.common.ide.session.completion.util.TailTextProvider
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItemTag
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionCloseness
import ru.hollowhorizon.hollowengine.common.scripting.ide.declarationCompletionItem

internal object CompletionItemFactory {
    context(_: KaSession)
    fun createCompletionItem(symbol: KaDeclarationSymbol, closeness: Int): CompletionItem.Declaration? {
        return createCompletionItem(middle = renderSymbolMiddle(symbol), symbol = symbol, closeness = closeness)
    }

    context(_: KaSession)
    fun createCompletionItem(signature: KaCallableSignature<*>, closeness: Int): CompletionItem.Declaration? {
        val symbol = signature.symbol
        return createCompletionItem(middle = renderSignatureMiddle(signature), symbol = symbol, closeness = closeness)
    }

    context(kaSession: KaSession)
    private fun createCompletionItem(
        middle: String?,
        symbol: KaDeclarationSymbol,
        closeness: Int,
    ): CompletionItem.Declaration? = with(kaSession) {
        val name = symbol.name?.asString() ?: ""
        if (name.contains(COMPLETION_FAKE_IDENTIFIER)) return null
        val (textToInsert, offset) = getInsertionText(symbol) ?: return null
        return declarationCompletionItem {
            this.show = name
            this.middle = middle
            this.insert = textToInsert
            tag = getCompletionItemTag(symbol)
            moveCaret = offset
            fqName = symbol.importableFqName?.asString()
            this.name = name
            this.closeness = maxOf(closeness, localCloseness(symbol))
            when (symbol) {
                is KaCallableSymbol -> {
                    tail = TailTextProvider.getTailText(symbol)
                }

                is KaClassLikeSymbol -> {
                    tail = TailTextProvider.getTailText(symbol)
                }

                else -> {}
            }
        }
    }

    /**
     * Locals and parameters are what the caret is standing in, so they lead the popup even when a
     * class from the index matches the typed prefix just as well.
     */
    private fun localCloseness(symbol: KaDeclarationSymbol): Int = when (symbol) {
        is KaLocalVariableSymbol,
        is KaValueParameterSymbol,
        is KaTypeParameterSymbol -> CompletionCloseness.LOCAL

        else -> CompletionCloseness.IMPORTED
    }

    private fun getCompletionItemTag(symbol: KaDeclarationSymbol): CompletionItemTag {
        return when (symbol) {
            is KaFunctionSymbol -> CompletionItemTag.FUNCTION
            is KaPropertySymbol -> CompletionItemTag.PROPERTY
            is KaClassLikeSymbol -> CompletionItemTag.CLASS
            else -> CompletionItemTag.LOCAL_VARIABLE
        }
    }

    context(_: KaSession)
    private fun getInsertionText(declaration: KaDeclarationSymbol): Pair<String, Int>? {
        return when {
            declaration is KaFunctionSymbol -> {
                val name = declaration.name?.asString() ?: return null
                when {
                    declaration.hasSingleFunctionTypeParameter() -> "$name {  }" to -2

                    declaration.valueParameters.isNotEmpty() -> "$name()" to -1
                    else -> "$name()" to 0
                }
            }

            else -> declaration.name?.asString()?.let { it to 0 }
        }
    }

    context(kaSession: KaSession)
    private fun KaFunctionSymbol.hasSingleFunctionTypeParameter(): Boolean = with(kaSession) {
        val singleParameter = valueParameters.singleOrNull() ?: return false
        val kind = singleParameter.returnType.functionTypeKind ?: return false
        kind == FunctionTypeKind.Function || kind == FunctionTypeKind.SuspendFunction
    }

    context(kaSession: KaSession)
    private fun renderSymbolMiddle(symbol: KaSymbol): String? = with(kaSession) {
        when (symbol) {
            is KaFunctionSymbol -> renderSignatureMiddle(symbol.asSignature())
            is KaVariableSymbol -> renderSignatureMiddle(symbol.asSignature())

            else -> null
        }
    }


    context(kaSession: KaSession)
    private fun renderSignatureMiddle(symbol: KaCallableSignature<*>): String? = with(kaSession) {
        when (symbol) {
            is KaFunctionSignature<*> ->
                buildString {
                    append("(")
                    symbol.valueParameters.forEachIndexed { i, p ->
                        append(p.name.asString())
                        append(": ")
                        append(p.returnType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.IN_VARIANCE))
                        if (i != symbol.valueParameters.lastIndex) {
                            append(", ")
                        }
                    }
                    append("): ")
                    append(symbol.returnType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.OUT_VARIANCE))
                }

            is KaVariableSignature<*> ->
                buildString {
                    append(": ")
                    append(symbol.returnType.render(KaTypeRendererForSource.WITH_SHORT_NAMES, Variance.OUT_VARIANCE))
                }
        }
    }
}