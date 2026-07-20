package ru.hollowhorizon.hollowengine.common.ide.session

import com.intellij.openapi.project.Project
import com.intellij.psi.impl.PsiFileEx
import com.intellij.psi.impl.PsiManagerEx
import org.jetbrains.kotlin.analysis.api.projectStructure.contextModule
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtUserType
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.ide.session.completion.createCompletions
import ru.hollowhorizon.hollowengine.common.ide.session.definition.findDefinition
import ru.hollowhorizon.hollowengine.common.ide.session.diagnostic.diagnosticCode
import ru.hollowhorizon.hollowengine.common.ide.session.highlight.highlightCode
import ru.hollowhorizon.hollowengine.common.ide.session.highlight.occurrencesCode
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaRekotLibraryModule
import ru.hollowhorizon.hollowengine.common.ide.session.modules.KaScriptModule
import ru.hollowhorizon.hollowengine.common.scripting.ide.*
import java.io.File

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

        val file = createPsiFile(name, original)

        fileCache[name] = CachedFile(textHash, textLength, file)

        return file
    }

    private fun createPsiFile(name: String, text: String): KtFile {
        val file = factory.createFile(name, text)
        val importedScripts = resolveImports(file)

        projectStructureProvider.setModule(
            file, KaScriptModule(
                file, project, buildList {
                    addAll(importedScripts.mapNotNull { it.contextModule })
                    addAll(libraries)
                    add(builtins.kaModule)
                }
            )
        )
        return file
    }

    private fun resolveImports(file: KtFile): List<KtFile> {
        val localPath =
            file.virtualFile.path.replace(File.separatorChar, '/').removePrefix("/").removePrefix("hollowengine/")
        val baseDir = DirectoryManager.HOLLOW_ENGINE.resolve(localPath).parent.toFile()

        return file.annotationEntries.filter { it.typeName == "Import" }.mapNotNull {
            val path = it.valueArguments[0].getArgumentExpression()?.text?.trim('"') ?: return@mapNotNull null
            runCatching {
                val fsFile = baseDir.resolve(path)

                createPsiFile(fsFile.relativeTo(DirectoryManager.HOLLOW_ENGINE.toFile()).toString(), fsFile.readText())
            }.getOrNull()
        }
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
    override fun occurrences(name: String, text: String, offset: Int): List<OccurrenceRange> {
        val file = getOrCreateFile(name, text)
        return occurrencesCode(file, offset)
    }

    @Synchronized
    override fun completions(name: String, text: String, offset: Int): List<CompletionItem> {
        val file = getOrCreateFile(name, text)
        return createCompletions(file, offset)
    }

    @Synchronized
    override fun definition(name: String, text: String, offset: Int): DefinitionLocation? {
        val file = getOrCreateFile(name, text)
        return findDefinition(file, offset)
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

val KtAnnotationEntry.typeName: String? get() = (typeReference?.typeElement as? KtUserType)?.referencedName
