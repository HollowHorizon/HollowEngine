package ru.hollowhorizon.hollowengine.common.ide.session

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.PsiFileEx
import com.intellij.psi.impl.PsiManagerEx
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import ru.hollowhorizon.hollowengine.common.ide.session.completion.createCompletions
import ru.hollowhorizon.hollowengine.common.ide.session.diagnostic.diagnosticCode
import ru.hollowhorizon.hollowengine.common.ide.session.highlight.highlightCode
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaRekotLibraryModule
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaScriptModule
import ru.hollowhorizon.hollowengine.common.scripting.ide.CompletionItem
import ru.hollowhorizon.hollowengine.common.scripting.ide.Diagnostic
import ru.hollowhorizon.hollowengine.common.scripting.ide.ScriptingAnalyzer
import ru.hollowhorizon.hollowengine.common.scripting.ide.TextLine

class ScriptingAnalyzerImpl(
    val kotlinCoreProjectEnvironment: KotlinCoreProjectEnvironment,
    val libraries: List<KaRekotLibraryModule>,
    val builtins: Builtins,
    val projectStructureProvider: ProjectStructureProviderImpl,
) : ScriptingAnalyzer {
    val project: Project
        get() = kotlinCoreProjectEnvironment.project
    private val factory = KtPsiFactory(project, eventSystemEnabled = true)

    internal val fileCache = object : LinkedHashMap<String, CachedFile>(5, 0.75f, true) {
        override fun removeEldestEntry(entry: MutableMap.MutableEntry<String, CachedFile>?): Boolean {
            val shouldRemove = size > 5
            if (shouldRemove && entry != null) {
                cleanupFile(entry.value.file)
            }
            return shouldRemove
        }
    }

    internal data class CachedFile(val textHash: Int, val textLength: Int, val file: KtFile)

    private fun getOrCreateFile(name: String, original: String): KtFile {
        val text = original.replace("\r\n", "\n")
        val textHash = text.hashCode()
        val textLength = text.length

        val cached = fileCache[name]

        if (cached != null) {
            if (cached.textLength == textLength && cached.textHash == textHash) {
                return cached.file
            }

            cleanupFile(cached.file)
        }

        val file = factory.createFile(name, text)
        projectStructureProvider.setModule(
            file, KaScriptModule(
                file, project, buildList {
                    addAll(libraries)
                    add(builtins.kaModule)
                }
            )
        )

        fileCache[name] = CachedFile(textHash, textLength, file)

        return file
    }

    internal fun cleanupFile(file: KtFile) {
        projectStructureProvider.removeModule(file)
        (file as? PsiFileEx)?.markInvalidated()
        removeFromPsiManager(file)
    }

    @Synchronized
    override fun highlight(
        name: String,
        text: String,
        offset: Int,
    ): List<TextLine> {
        val file = getOrCreateFile(name, text)
        return highlightCode(file, offset)
    }

    @Synchronized
    override fun completions(name: String, text: String, offset: Int): List<CompletionItem> {
        val file = getOrCreateFile(name, text)
        return createCompletions(file, offset)
    }

    @Synchronized
    override fun diagnostic(name: String, text: String): List<Diagnostic> {
        val file = getOrCreateFile(name, text)
        return diagnosticCode(file)
    }
}

private fun removeFromPsiManager(file: KtFile) {
    val psiManager = com.intellij.psi.PsiManager.getInstance(file.project) as? PsiManagerEx ?: return
    val fileManager = psiManager.fileManager
    fileManager.setViewProvider(file.virtualFile, null)
}
