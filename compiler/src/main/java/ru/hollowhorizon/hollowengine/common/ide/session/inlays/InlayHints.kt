package ru.hollowhorizon.hollowengine.common.ide.session.inlays


import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.StubBasedPsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.KaSuccessCallInfo
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.lexer.KtTokens.DOT
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import ru.hollowhorizon.hollowengine.client.gui.scripting.files.text.util.InlayHint
import ru.hollowhorizon.hollowengine.common.ide.session.completion.util.renderVerbose
import ru.hollowhorizon.hollowengine.logE
val KtNamedDeclaration.isSingleUnderscore: Boolean
    get() {
        // We don't want to call 'getNameIdentifier' on stubs to prevent text building
        // But it's fine because one-underscore names are prohibited for non-local declarations (only lambda parameters, local vars are allowed)
        if (this is StubBasedPsiElement<*> && this.stub != null) return false
        return nameIdentifier?.text == "_"
    }


context(session: KaSession)
private fun PsiElement.determineType(): KaType? =
    with(session) {
        when (this@determineType) {
            is KtNamedFunction -> symbol.returnType
            is KtCallExpression -> expressionType
            is KtParameter if isLambdaParameter && typeReference == null -> returnType
            is KtDestructuringDeclarationEntry if !isSingleUnderscore -> expressionType
            is KtProperty -> returnType.takeIf { this !is KaErrorType }
            else -> null
        }
    }

@Suppress("ReturnCount")
context(session: KaSession)
private fun PsiElement.hintBuilder(kind: InlayKind, label: String? = null): InlayHint? {
    val element = when (this) {
        is KtFunction -> this.valueParameterList!!.originalElement
        is PsiNameIdentifierOwner -> this.nameIdentifier
        else -> this
    } ?: return null

    val hint = when (kind) {
        InlayKind.ParameterHint -> InlayHint(element.textRange.startOffset, "$label=")
        else ->
            this.determineType()?.let {
                InlayHint(element.textRange.endOffset, ": " + with(session) { it.renderVerbose() })
            } ?: return null
    }
    return hint
}

@Suppress("ReturnCount")
context(session: KaSession)
private fun callableArgNameHints(
    acc: MutableList<InlayHint>,
    callExpression: KtCallExpression,
): Unit = with(session) {
    val callInfo = callExpression.resolveToCall()
    val successfulCallInfo = callInfo as? KaSuccessCallInfo ?: return

    val functionCall = successfulCallInfo.call as? KaFunctionCall<*> ?: return

    for ((argExpression, variableSignature) in functionCall.argumentMapping) {

        val valueArgument = argExpression.parent as? KtValueArgument ?: continue

        // --- Правила фильтрации (когда подсказку показывать НЕ надо) ---

        // A. Если аргумент уже именован явно: foo(param = 1)
        if (valueArgument.isNamed()) continue

        // B. Если аргумент — это лямбда (особенно trailing lambda): list.forEach { ... }
        // Подсказка "action: { ... }" обычно считается шумом.
        if (valueArgument is KtLambdaArgument) continue

        // Получаем имя параметра из сигнатуры
        val paramName = variableSignature.symbol.name.asString()

        // C. (Эвристика IDE) Не показывать подсказку, если имя передаваемой переменной совпадает с именем параметра.
        // Пример: fun setAlpha(alpha: Float). Вызов: setAlpha(alpha).
        // Подсказка "alpha: alpha" избыточна.
        if (argExpression is KtNameReferenceExpression && argExpression.getReferencedName() == paramName) {
            continue
        }

        val hint = argExpression.hintBuilder(InlayKind.ParameterHint, paramName)
        if (hint != null) {
            acc.add(hint)
        }
    }
}

context(session: KaSession)
private fun lambdaValueParamHints(
    acc: MutableList<InlayHint>,
    node: KtLambdaArgument,
) {

    val params = node.getLambdaExpression()!!.valueParameters

    //hint should not be rendered when parameter is of type DestructuringDeclaration
    //example: Map.forEach { (k,v) -> _ }
    //lambda parameter (k,v) becomes (k :hint, v :hint) :hint <- outer hint isnt needed
    params.singleOrNull()?.let {
        if (it.destructuringDeclaration != null) return
    }

    val hints = params.mapNotNull {
        it.hintBuilder(InlayKind.TypeHint)
    }
    acc.addAll(hints)
}

context(session: KaSession)
private fun chainedExpressionHints(
    acc: MutableList<InlayHint>,
    node: KtDotQualifiedExpression,
) {
    ///chaining is defined as an expression whose next sibling tokens are newline and dot
    val next = (node.nextSibling as? PsiWhiteSpace)
    val nextSiblingElement = next?.nextSibling?.node?.elementType

    if (nextSiblingElement != null && nextSiblingElement == DOT) {
        val hints = node.getChildrenOfType<KtCallExpression>().mapNotNull {
            it.hintBuilder(InlayKind.ChainingHint)
        }
        acc.addAll(hints)
    }
}

context(session: KaSession)
private fun destructuringVarHints(
    acc: MutableList<InlayHint>,
    node: KtDestructuringDeclaration,
) {
    val hints = node.entries.mapNotNull {
        it.hintBuilder(InlayKind.TypeHint)
    }
    acc.addAll(hints)
}

context(session: KaSession)
private fun declarationHint(
    acc: MutableList<InlayHint>,
    node: KtProperty,
) {
    //check decleration does not include type i.e. var t1: String
    if (node.typeReference != null) return

    val hint = node.hintBuilder(InlayKind.TypeHint) ?: return
    acc.add(hint)
}

context(session: KaSession)
private fun functionHint(
    acc: MutableList<InlayHint>,
    node: KtNamedFunction,
) {
    //only render hints for functions without block body
    //functions WITH block body will always specify return types explicitly
    if (!node.hasDeclaredReturnType() && !node.hasBlockBody()) {
        val hint = node.hintBuilder(InlayKind.TypeHint) ?: return
        acc.add(hint)
    }
}

context(session: KaSession)
fun provideHints(file: KtFile): List<InlayHint> {
    val res = mutableListOf<InlayHint>()
    for (node in file.preOrderTraversal().asIterable()) {
        try {
            when (node) {
                is KtNamedFunction -> functionHint(res, node)
                is KtLambdaArgument -> lambdaValueParamHints(res, node)
                // TODO: chained expressions обычно в Java применяют, в Kotlin от них толку мало, но можно будет в конфиг добавить
                // is KtDotQualifiedExpression -> chainedExpressionHints(res, node)
                is KtCallExpression -> callableArgNameHints(res, node)
                is KtDestructuringDeclaration -> destructuringVarHints(res, node)
                is KtProperty -> declarationHint(res, node)
            }
        } catch (e: Throwable) {
            logE(e)
        }
    }
    return res
}

fun PsiElement.preOrderTraversal(shouldTraverse: (PsiElement) -> Boolean = { true }): Sequence<PsiElement> {
    val root = this

    return sequence {
        if (shouldTraverse(root)) {
            yield(root)

            for (child in root.children) {
                if (shouldTraverse(child)) {
                    yieldAll(child.preOrderTraversal(shouldTraverse))
                }
            }
        }
    }
}

enum class InlayKind {
    TypeHint,
    ParameterHint,
    ChainingHint,
}