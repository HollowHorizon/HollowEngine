package ru.hollowhorizon.hollowengine.common.ide.session.definition

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import ru.hollowhorizon.hollowengine.common.ide.session.ScriptingAnalyzerImpl
import ru.hollowhorizon.hollowengine.common.ide.session.inlays.resourceLocationDefinition
import ru.hollowhorizon.hollowengine.common.scripting.ide.DefinitionLocation
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.zip.ZipEntry
import kotlin.io.path.absolutePathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension

fun ScriptingAnalyzerImpl.findDefinition(file: KtFile, offset: Int): DefinitionLocation? {
    resourceLocationDefinition(file, offset)?.let { return it }
    val expression = file.referenceExpressionAt(offset) ?: return null
    return analyze(file) {
        val symbols = expression.mainReference.resolveToSymbols().toList()
        val symbol = symbols.pickDefinitionSymbol() ?: return@analyze null
        definitionForSymbol(symbol)
    }
}

private fun KtFile.referenceExpressionAt(offset: Int): KtSimpleNameExpression? {
    if (textLength == 0) return null
    val safeOffset = offset.coerceIn(0, textLength)
    return sequenceOf(safeOffset, safeOffset - 1)
        .filter { it in 0 until textLength }
        .mapNotNull { findElementAt(it) }
        .flatMap { it.parentsWithSelf }
        .filterIsInstance<KtSimpleNameExpression>()
        .firstOrNull()
}

private fun List<KaSymbol>.pickDefinitionSymbol(): KaDeclarationSymbol? {
    if (isEmpty()) return null
    return firstNotNullOfOrNull { it as? KaConstructorSymbol }
        ?: firstNotNullOfOrNull { it as? KaClassLikeSymbol }
        ?: firstNotNullOfOrNull { it as? KaCallableSymbol }
        ?: firstNotNullOfOrNull { it as? KaDeclarationSymbol }
}

private fun ScriptingAnalyzerImpl.definitionForSymbol(symbol: KaDeclarationSymbol): DefinitionLocation? {
    val targetPsi = symbol.psi
    runCatching {
        targetPsi?.toDefinitionLocation()?.let { return it }
    }

    val classFile = targetPsi?.containingFile?.virtualFile
    val source = LibraryDefinitionSources.find(symbol, classFile, libraries)
    if (source != null) return source

    return classFile?.let { CfrDefinitionDecompiler.decompile(it, symbol.definitionSearchName()) }
}

private fun PsiElement.toDefinitionLocation(): DefinitionLocation? {
    val containingFile = containingFile ?: return null
    val virtualFile = containingFile.virtualFile
    val offset = (this as? PsiNameIdentifierOwner)?.nameIdentifier?.textRange?.startOffset
        ?: (this as? KtNamedDeclaration)?.nameIdentifier?.startOffset
        ?: (this as? PsiNamedElement)?.textRange?.startOffset
        ?: textRange?.startOffset
        ?: 0

    if (virtualFile == null || !virtualFile.path.contains("!/")) {
        return DefinitionLocation(containingFile.name, offset)
    }

    if (virtualFile.extension == "kt" || virtualFile.extension == "java") {
        val text = virtualFile.readTextOrNull() ?: return null
        val jarName = virtualFile.path.substringBefore("!/").substringAfterLast('/')
        val entryName = virtualFile.path.substringAfter("!/", virtualFile.name)
        return DefinitionLocation(
            path = "library-sources/$jarName!/$entryName",
            offset = offset.coerceIn(0, text.length),
            text = text,
            readOnly = true,
        )
    }

    return null
}

private object LibraryDefinitionSources {
    private val sourceJarCandidatesCache = linkedMapOf<String, List<Path>>()

    fun find(
        symbol: KaDeclarationSymbol,
        classFile: VirtualFile?,
        libraries: Collection<org.jetbrains.kotlin.analysis.api.projectStructure.KaLibraryModule>,
    ): DefinitionLocation? {
        val names = classFile?.sourceLookupNames(symbol.definitionSearchName())
            ?: symbol.sourceLookupNames()
        if (names.isEmpty) return null
        for (sourceJar in libraries.asSequence().flatMap { it.binaryRoots.asSequence() }.sourceJars()) {
            val location = findInSourceJar(sourceJar, names)
            if (location != null) return location
        }
        return null
    }

    private fun Sequence<Path>.sourceJars(): Sequence<Path> {
        return flatMap { root ->
            sequence {
                if (root.isSourceJar()) yield(root)
                root.sourceJarCandidatesCached().forEach { candidate ->
                    if (candidate.isSourceJar()) yield(candidate)
                }
            }
        }.distinct()
    }

    private fun Path.isSourceJar(): Boolean {
        return isRegularFile() && name.endsWith(".jar") && name.contains("sources", ignoreCase = true)
    }

    private fun Path.sourceJarCandidatesCached(): List<Path> {
        val key = absolutePathString()
        return sourceJarCandidatesCache.getOrPut(key) { sourceJarCandidates() }
    }

    private fun Path.sourceJarCandidates(): List<Path> = buildList {
        if (!name.endsWith(".jar")) return@buildList
        parent?.resolve("${nameWithoutExtension}-sources.jar")?.let { add(it) }
        val versionDirectory = parent?.parent
        versionDirectory?.let { directory ->
            val candidates = runCatching {
                Files.walk(directory, 2).use { paths ->
                    paths.filter { it.name.endsWith("-sources.jar") }.toList()
                }
            }.getOrDefault(emptyList())
            addAll(candidates)
        }
    }

    private fun findInSourceJar(sourceJar: Path, names: SourceLookupNames): DefinitionLocation? {
        return runCatching {
            JarFile(sourceJar.toFile()).use { jar ->
                names.directEntries()
                    .firstNotNullOfOrNull { entry -> jar.getEntry(entry)?.let { entry to jar.readEntry(it) } }
                    ?.let { (entry, text) ->
                        return DefinitionLocation(
                            path = "library-sources/${sourceJar.name}!/$entry",
                            offset = names.findOffset(text),
                            text = text,
                            readOnly = true,
                        )
                    }
            }
            null
        }.getOrNull()
    }
}

private object CfrDefinitionDecompiler {
    private val cache = linkedMapOf<String, String>()

    fun decompile(classFile: VirtualFile, symbolName: String?): DefinitionLocation? {
        val classPath = classFile.path
        if (!classPath.endsWith(".class") || "!/" !in classPath) return null
        val key = classPath
        val text = cache.getOrPut(key) {
            decompileClass(classPath) ?: return null
        }
        return DefinitionLocation(
            path = "decompiled/${classPath.substringAfterLast("!/").removeSuffix(".class")}.java",
            offset = symbolName?.let { text.findWordOffset(it) } ?: 0,
            text = text,
            readOnly = true,
        )
    }

    private fun decompileClass(classPath: String): String? {
        val jarPath = classPath.substringBefore("!/")
        val entryName = classPath.substringAfter("!/")
        val tempRoot = Files.createTempDirectory("hollowengine-cfr-")
        return try {
            JarFile(File(jarPath)).use { jar ->
                val outerClassPrefix = entryName.removeSuffix(".class").substringBefore('$')
                jar.entries().asSequence()
                    .filter { !it.isDirectory && it.name.endsWith(".class") && it.name.removeSuffix(".class").substringBefore('$') == outerClassPrefix }
                    .forEach { entry ->
                        val target = tempRoot.resolve(entry.name)
                        Files.createDirectories(target.parent)
                        jar.getInputStream(entry).use { input ->
                            Files.copy(input, target)
                        }
                    }
            }
            val targetClass = tempRoot.resolve(entryName)
            CfrSourceDecompiler.decompile(targetClass.absolutePathString())
        } finally {
            tempRoot.toFile().deleteRecursively()
        }
    }
}

private data class SourceLookupNames(
    val packagePath: String,
    val classNames: List<String>,
    val declarationName: String?,
) {
    val isEmpty: Boolean get() = classNames.isEmpty() && declarationName.isNullOrBlank()

    fun directEntries(): Sequence<String> = sequence {
        for (className in classNames) {
            yield("$packagePath$className.kt")
            yield("$packagePath$className.java")
        }
    }

    fun matchesSource(text: String): Boolean {
        val declaration = declarationName ?: return false
        return text.contains(Regex("""\b${Regex.escape(declaration)}\b"""))
    }

    fun findOffset(text: String): Int {
        declarationName?.let { name ->
            text.findWordOffset(name)?.let { return it }
        }
        for (className in classNames) {
            text.findWordOffset(className)?.let { return it }
        }
        return 0
    }
}

private fun KaDeclarationSymbol.sourceLookupNames(): SourceLookupNames {
    if (this is KaClassLikeSymbol) {
        val classId = classId
        if (classId != null) {
            val packagePath = classId.packageFqName.asString().replace('.', '/').let { if (it.isBlank()) "" else "$it/" }
            val relativeName = classId.relativeClassName.asString().replace('.', '/')
            val classCandidates = relativeName.classSourceCandidates()
            return SourceLookupNames(packagePath, classCandidates, definitionSearchName())
        }
    }

    return SourceLookupNames("", definitionSearchName()?.let(::listOf).orEmpty(), definitionSearchName())
}

private fun VirtualFile.sourceLookupNames(declarationName: String?): SourceLookupNames? {
    val entry = path.substringAfter("!/", missingDelimiterValue = "")
    if (!entry.endsWith(".class")) return null
    val classPath = entry.removeSuffix(".class").substringBefore('$')
    val packagePath = classPath.substringBeforeLast('/', "").let { if (it.isBlank()) "" else "$it/" }
    val relativeName = classPath.substringAfterLast('/')
    return SourceLookupNames(packagePath, relativeName.classSourceCandidates(), declarationName)
}

private fun KaDeclarationSymbol.definitionSearchName(): String? {
    return when (this) {
        is KaClassLikeSymbol -> name?.asString()
        is KaCallableSymbol -> callableId?.callableName?.asString() ?: (psi as? PsiNamedElement)?.name
        else -> (psi as? PsiNamedElement)?.name
    }
}

private fun String.classSourceCandidates(): List<String> {
    val parts = split('/')
    return parts.indices.reversed().map { index -> parts.take(index + 1).joinToString("/") }
}

private fun JarFile.readEntry(entry: ZipEntry): String {
    return getInputStream(entry).bufferedReader().use { it.readText() }
}

private fun VirtualFile.readTextOrNull(): String? {
    return runCatching { inputStream.bufferedReader().use { it.readText() } }.getOrNull()
}

private fun String.findWordOffset(word: String): Int? {
    return Regex("""\b${Regex.escape(word)}\b""").find(this)?.range?.first
}
