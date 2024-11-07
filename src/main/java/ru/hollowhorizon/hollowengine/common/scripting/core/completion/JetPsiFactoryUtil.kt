package ru.hollowhorizon.hollowengine.common.scripting.core.completion

import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.vfs.CharsetToolkit
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtFile

object JetPsiFactoryUtil {

    fun createFile(project: Project, name: String, text: String): KtFile {
        val nameWithExtension = if (name.endsWith(".kt")) name else "$name.kt"
        val virtualFile = LightVirtualFile(nameWithExtension, KotlinLanguage.INSTANCE, text)
        virtualFile.charset = CharsetToolkit.UTF8_CHARSET
        return (PsiFileFactory.getInstance(project) as PsiFileFactoryImpl).trySetupPsiForFile(virtualFile, KotlinLanguage.INSTANCE, true, false) as KtFile
    }

}