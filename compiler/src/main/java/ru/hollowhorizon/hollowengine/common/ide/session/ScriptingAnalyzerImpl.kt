package ru.hollowhorizon.hollowengine.common.ide.session

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import ru.hollowhorizon.hollowengine.common.ide.session.diagnostic.diagnosticCode
import ru.hollowhorizon.hollowengine.common.ide.session.highlight.highlightCode
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaRekotLibraryModule
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaScriptModule
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.TextLine

class ScriptingAnalyzerImpl(
    val kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
    val libraries: List<KaRekotLibraryModule>,
    val builtins: Builtins,
    val projectStructureProvider: ProjectStructureProviderImpl,
): ScriptingAnalyzer {
    val project: Project
        get() = kotlinCoreProjectEnvironment.project
    private val factory = KtPsiFactory(project, eventSystemEnabled = true)

    fun createFile(name: String, text: String): KtFile {
        val file = factory.createFile(name, text)
        projectStructureProvider.setModule(file, KaScriptModule(
            file, project, buildList {
                addAll(libraries)
                add(builtins.kaModule)
            }
        ))
        return file
    }

    override fun highlight(
        name: String,
        text: String,
        offset: Int
    ): List<TextLine> {
        val file = createFile(name, text)
        return highlightCode(file, offset)
    }

    override fun diagnostic(name: String, text: String): List<Diagnostic> {
        val file = createFile(name, text)
        return diagnosticCode(file)
    }
}