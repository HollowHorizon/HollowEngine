package ru.hollowhorizon.hollowengine.common.ide.session.diagnostic

import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.Range
import ru.hollowhorizon.hollowengine.common.scripting.ide.Severity

private val SubscribeEventClassId =
    ClassId.topLevel(FqName("ru.hollowhorizon.hollowengine.common.events.SubscribeEvent"))
private val EventClassId = ClassId.topLevel(FqName("ru.hollowhorizon.hollowengine.common.events.Event"))

/**
 * Checks that every `@SubscribeEvent` is something the event bus can actually register.
 */
internal fun subscribeEventDiagnostics(file: KtFile): List<Diagnostic> {
    val candidates = PsiTreeUtil.collectElementsOfType(file, KtNamedFunction::class.java)
        .mapNotNull { function -> function.subscribeEventEntry()?.let { function to it } }
    if (candidates.isEmpty()) return emptyList()

    val document = file.fileDocument
    val diagnostics = ArrayList<Diagnostic>()

    analyze(file) {
        for ((function, entry) in candidates) {
            val symbol = runCatching { function.symbol as? KaNamedFunctionSymbol }.getOrNull()
            if (symbol != null && symbol.annotations.isNotEmpty() && SubscribeEventClassId !in symbol.annotations) {
                continue
            }

            val owner = function.containingClassOrObject
            if (owner != null) {
                val ownerSymbol = runCatching { owner.symbol as? KaClassSymbol }.getOrNull()
                if (ownerSymbol?.classKind != KaClassKind.OBJECT) {
                    diagnostics += Diagnostic(
                        document.rangeOf(entry),
                        Severity.WARNING,
                        "@SubscribeEvent inside '${owner.name ?: "this class"}' is never registered: " +
                                "a handler has to be a top-level function of the script or a member of an object",
                    )
                }
            }

            if (function.hasModifier(KtTokens.SUSPEND_KEYWORD)) {
                diagnostics += Diagnostic(
                    document.rangeOf(entry),
                    Severity.ERROR,
                    "@SubscribeEvent handler cannot be a suspend function: the bus calls it directly",
                )
            }

            if (function.typeParameters.isNotEmpty()) {
                diagnostics += Diagnostic(
                    document.rangeOf(entry),
                    Severity.ERROR,
                    "@SubscribeEvent handler cannot declare type parameters",
                )
            }

            if (function.receiverTypeReference != null) {
                diagnostics += Diagnostic(
                    document.rangeOf(entry),
                    Severity.ERROR,
                    "@SubscribeEvent handler cannot be an extension function: the bus has no receiver to call it on",
                )
            }

            val parameters = function.valueParameters
            if (parameters.size != 1) {
                diagnostics += Diagnostic(
                    document.rangeOf(function.valueParameterList ?: function.nameIdentifier ?: entry),
                    Severity.ERROR,
                    "@SubscribeEvent handler must take exactly one Event parameter, but takes ${parameters.size}",
                )
                continue
            }

            val parameter = parameters.single()
            val parameterType = symbol?.valueParameters?.singleOrNull()?.returnType
            if (parameterType != null && !parameterType.isSubtypeOf(EventClassId)) {
                diagnostics += Diagnostic(
                    document.rangeOf(parameter.typeReference ?: parameter),
                    Severity.ERROR,
                    "@SubscribeEvent parameter must be an Event, but is " +
                            "'${parameter.typeReference?.text ?: parameter.name}'",
                )
            }

            val returnType = symbol?.returnType
            if (returnType != null && !returnType.isUnitType) {
                diagnostics += Diagnostic(
                    document.rangeOf(function.typeReference ?: entry),
                    Severity.ERROR,
                    "@SubscribeEvent handler must return Unit, but returns " +
                            "'${function.typeReference?.text ?: "a value"}'",
                )
            }
        }
    }
    return diagnostics
}

/** The `@SubscribeEvent` entry on this function, matched by short name before anything is resolved. */
private fun KtNamedFunction.subscribeEventEntry(): KtAnnotationEntry? = annotationEntries.firstOrNull { entry ->
    entry.typeReference?.text?.substringBefore('<')?.substringAfterLast('.')?.trim() == "SubscribeEvent"
}

private fun Document.rangeOf(element: PsiElement): Range = rangeOf(element.textRange)
