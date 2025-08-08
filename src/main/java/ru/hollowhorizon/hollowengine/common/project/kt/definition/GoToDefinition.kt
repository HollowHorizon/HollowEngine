package ru.hollowhorizon.hollowengine.common.project.kt.definition

import org.eclipse.lsp4j.Location
import java.nio.file.Path
import ru.hollowhorizon.hollowengine.common.project.kt.CompiledFile
import ru.hollowhorizon.hollowengine.common.project.kt.CompilerClassPath
import ru.hollowhorizon.hollowengine.common.project.kt.ExternalSourcesConfiguration
import ru.hollowhorizon.hollowengine.common.project.kt.externalsources.ClassContentProvider
import ru.hollowhorizon.hollowengine.common.project.kt.externalsources.KlsURI
import ru.hollowhorizon.hollowengine.common.project.kt.position.location
import ru.hollowhorizon.hollowengine.common.project.kt.util.TemporaryDirectory
import ru.hollowhorizon.hollowengine.common.project.kt.util.parseFile
import org.jetbrains.kotlin.js.resolve.diagnostics.findPsi
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import ru.hollowhorizon.hollowengine.HollowEngine
import java.io.File

private val cachedTempFiles = mutableMapOf<KlsURI, Path>()
private val definitionPattern = Regex("(?:class|interface|object|fun)\\s+(\\w+)")

fun goToDefinition(
    file: CompiledFile,
    cursor: Int,
    cp: CompilerClassPath
): Location? {
    val (_, target) = file.referenceExpressionAtPoint(cursor) ?: return null

    HollowEngine.LOGGER.info("Found declaration descriptor {}", target)
    var destination = location(target)
    val psi = target.findPsi()

    if (psi is KtNamedDeclaration) {
        destination = psi.nameIdentifier?.let(::location) ?: destination
    }

    if (destination != null) {
        val rawClassURI = destination.uri

        // TODO Check inside archive
    }

    return destination
}

private fun isInsideArchive(uri: String, cp: CompilerClassPath) =
    uri.contains(".jar!") || uri.contains(".zip!") || cp.javaHome?.let {
        parseFile(uri).toPath().toString().startsWith(File(it).path)
    } ?: false
