package ru.hollowhorizon.hollowengine.common.ide.session.completion

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.lexer.KtToken

internal const val COMPLETION_FAKE_IDENTIFIER = "TheOneWhoPurifies"

internal fun matchesPrefix(prefix: String?, item: String): Boolean {
    if (prefix == null) return true
    return item.startsWith(prefix, ignoreCase = true)
}

internal fun <T> Result<T>.getOrHandleException(onException: (Throwable) -> Unit): T? {
    try {
        return getOrThrow()
    } catch (e: Throwable) {
        onException(e)
        return null
    }
}

val PsiElement.tokenType: KtToken?
    get() = node.elementType as? KtToken