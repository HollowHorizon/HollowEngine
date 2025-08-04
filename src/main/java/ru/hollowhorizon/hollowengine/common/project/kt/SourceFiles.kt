package ru.hollowhorizon.hollowengine.common.project.kt

import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.jetbrains.kotlin.com.intellij.lang.Language
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import org.jetbrains.kotlin.idea.KotlinLanguage
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.project.kt.util.describeURI
import ru.hollowhorizon.hollowengine.common.project.kt.util.describeURIs
import java.io.*
import java.nio.file.FileSystems
import java.nio.file.Path

private class SourceVersion(val content: String, val version: Int, val language: Language?, val isTemporary: Boolean)

/**
 * Notify SourcePath whenever a file changes
 */
private class NotifySourcePath(private val sp: SourcePath) {
    private val files = mutableMapOf<File, SourceVersion>()

    operator fun get(uri: File): SourceVersion? = files[uri]

    operator fun set(uri: File, source: SourceVersion) {
        val content = convertLineSeparators(source.content)

        files[uri] = source
        sp.put(uri, content, source.language, source.isTemporary)
    }

    fun remove(uri: File) {
        files.remove(uri)
        sp.delete(uri)
    }

    fun removeIfTemporary(uri: File): Boolean =
        if (sp.deleteIfTemporary(uri)) {
            files.remove(uri)
            true
        } else {
            false
        }

    fun removeAll(rm: Collection<File>) {
        files -= rm

        rm.forEach(sp::delete)
    }

    val keys get() = files.keys
}

/**
 * Keep track of the text of all files in the workspace
 */
class SourceFiles(
    private val sp: SourcePath,
    private val contentProvider: URIContentProvider,
    private val scriptsConfig: ScriptsConfiguration,
) {
    private val workspaceRoots = mutableSetOf<Path>()
    private var exclusions = SourceExclusions(workspaceRoots, scriptsConfig)
    private val files = NotifySourcePath(sp)
    private val open = mutableSetOf<File>()

    fun open(uri: File, content: String, version: Int) {
        files[uri] = SourceVersion(content, version, languageOf(uri), isTemporary = false)
        open.add(uri)
    }

    fun close(uri: File) {
        if (uri in open) {
            open.remove(uri)
            val removed = files.removeIfTemporary(uri)

            if (!removed) {
                val disk = readFromDisk(uri, temporary = false)

                if (disk != null) {
                    files[uri] = disk
                } else {
                    files.remove(uri)
                }
            }
        }
    }

    fun edit(uri: File, newVersion: Int, contentChanges: List<TextDocumentContentChangeEvent>) {
        val existing = files[uri]!!
        var newText = existing.content

        if (newVersion <= existing.version) {
            HollowEngine.LOGGER.warn("Ignored {} version {}", describeURI(uri), newVersion)
            return
        }

        for (change in contentChanges) {
            if (change.range == null) newText = change.text
            else newText = patch(newText, change)
        }

        uri.toPath().toFile().writeText(newText, Charsets.UTF_8)
        files[uri] = SourceVersion(newText, newVersion, existing.language, existing.isTemporary)
    }

    fun createdOnDisk(uri: File) {
        changedOnDisk(uri)
    }

    fun deletedOnDisk(uri: File) {
        if (isSource(uri)) {
            files.remove(uri)
        }
    }

    fun changedOnDisk(uri: File) {
        if (isSource(uri)) {
            files[uri] = readFromDisk(uri, files[uri]?.isTemporary ?: true)
                ?: error("Could not read source file '$uri' after being changed on disk")
        }
    }

    private fun readFromDisk(uri: File, temporary: Boolean): SourceVersion? = try {
        val content = contentProvider.contentOf(uri)
        SourceVersion(content, -1, languageOf(uri), isTemporary = temporary)
    } catch (e: FileNotFoundException) {
        null
    } catch (e: IOException) {
        HollowEngine.LOGGER.warn("Exception while reading source file {}", describeURI(uri))
        null
    }

    private fun isSource(uri: File): Boolean = isIncluded(uri) && languageOf(uri) != null

    private fun languageOf(uri: File): Language? {
        val fileName = uri.name
        return when {
            fileName.endsWith(".kt") || fileName.endsWith(".kts") -> KotlinLanguage.INSTANCE
            else -> null
        }
    }

    fun addWorkspaceRoot(root: Path) {
        HollowEngine.LOGGER.info("Searching $root using exclusions: ${exclusions.excludedPatterns}")
        val addSources = findSourceFiles(root)

        logAdded(addSources, root)

        for (uri in addSources) {
            readFromDisk(uri, temporary = false)?.let {
                files[uri] = it
            } ?: HollowEngine.LOGGER.warn("Could not read source file '{}'", uri.path)
        }

        workspaceRoots.add(root)
        updateExclusions()
    }

    fun removeWorkspaceRoot(root: Path) {
        val rmSources = files.keys.filter { it.toPath().startsWith(root) }

        logRemoved(rmSources, root)

        files.removeAll(rmSources)
        workspaceRoots.remove(root)
        updateExclusions()
    }

    private fun findSourceFiles(root: Path): Set<File> {
        val sourceMatcher = FileSystems.getDefault().getPathMatcher("glob:*.{kt,kts}")
        return SourceExclusions(listOf(root), scriptsConfig)
            .walkIncluded()
            .filter { sourceMatcher.matches(it.fileName) }
            .map(Path::toFile)
            .toSet()
    }

    fun updateExclusions() {
        exclusions = SourceExclusions(workspaceRoots, scriptsConfig)
        HollowEngine.LOGGER.info("Updated exclusions: ${exclusions.excludedPatterns}")
    }

    fun isOpen(uri: File): Boolean = (uri in open)

    fun isIncluded(uri: File): Boolean = exclusions.isURIIncluded(uri)
}

private fun patch(sourceText: String, change: TextDocumentContentChangeEvent): String {
    val range = change.range
    val reader = BufferedReader(StringReader(sourceText))
    val writer = StringWriter()

    // Skip unchanged lines
    var line = 0

    while (line < range.start.line) {
        writer.write(reader.readLine() + '\n')
        line++
    }

    // Skip unchanged chars
    for (character in 0 until range.start.character) {
        writer.write(reader.read())
    }

    // Write replacement text
    writer.write(change.text)

    // Skip replaced text
    for (i in 0 until (range.end.line - range.start.line)) {
        reader.readLine()
    }
    if (range.start.line == range.end.line) {
        reader.skip((range.end.character - range.start.character).toLong())
    } else {
        reader.skip(range.end.character.toLong())
    }

    // Write remaining text
    while (true) {
        val next = reader.read()

        if (next == -1) return writer.toString()
        else writer.write(next)
    }
}

private fun logAdded(sources: Collection<File>, rootPath: Path?) {
    HollowEngine.LOGGER.info("Adding {} under {} to source path", describeURIs(sources), rootPath)
}

private fun logRemoved(sources: Collection<File>, rootPath: Path?) {
    HollowEngine.LOGGER.info("Removing {} under {} to source path", describeURIs(sources), rootPath)
}
