package ru.hollowhorizon.hollowengine.common.project.kt.j2k

// import org.jetbrains.kotlin.j2k.JavaToKotlinTranslator
import org.jetbrains.kotlin.com.intellij.lang.java.JavaLanguage
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.project.kt.compiler.Compiler
import ru.hollowhorizon.hollowengine.common.project.kt.compiler.CompilationKind
import ru.hollowhorizon.hollowengine.common.project.kt.util.nonNull

fun convertJavaToKotlin(javaCode: String, compiler: Compiler): String {
    val psiFactory = compiler.psiFileFactoryFor(CompilationKind.DEFAULT)
    val javaAST = psiFactory.createFileFromText("snippet.java", JavaLanguage.INSTANCE, javaCode)
    HollowEngine.LOGGER.info("Parsed {} to {}", javaCode, javaAST)

	return JavaElementConverter().also(javaAST::accept).translatedKotlinCode ?: run {
        HollowEngine.LOGGER.warn("Could not translate code")
        ""
    }
}
