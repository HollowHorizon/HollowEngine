package ru.hollowhorizon.hollowengine.common.project.kt.j2k

import org.jetbrains.kotlin.com.intellij.lang.java.JavaLanguage
import ru.hollowhorizon.hollowengine.common.project.kt.compiler.CompilationKind
import ru.hollowhorizon.hollowengine.common.project.kt.compiler.Compiler

fun convertJavaToKotlin(javaCode: String, compiler: Compiler): String {
    val psiFactory = compiler.psiFileFactoryFor(CompilationKind.DEFAULT)
    val javaAST = psiFactory.createFileFromText("snippet.java", JavaLanguage.INSTANCE, javaCode)

    return JavaElementConverter().also(javaAST::accept).translatedKotlinCode ?: run {
        ""
    }
}
