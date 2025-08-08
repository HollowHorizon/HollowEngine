package ru.hollowhorizon.hollowengine.common.project.kt.util

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiFile
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import java.nio.file.Path
import java.nio.file.Paths

inline fun<reified Find> PsiElement.findParent() =
        this.parentsWithSelf.filterIsInstance<Find>().firstOrNull()

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

fun PsiFile.toPath(): Path {
    val file = this.originalFile.viewProvider.virtualFile
    return (file as? LightVirtualFile)?.originalFile?.name?.let { Paths.get(it) } ?: winCompatiblePathOf(file.path)
}
