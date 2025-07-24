package ru.hollowhorizon.hollowengine.common.project.kt

import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import java.net.URI
import java.nio.file.Paths
import ru.hollowhorizon.hollowengine.common.project.kt.externalsources.ClassContentProvider
import ru.hollowhorizon.hollowengine.common.project.kt.externalsources.toKlsURI
import kotlin.io.path.readText

/**
 * Fetches the content of Kotlin files identified by a URI.
 */
class URIContentProvider(
    val classContentProvider: ClassContentProvider
) {
    fun contentOf(uri: URI): String = when (uri.scheme) {
        "file" -> Paths.get(uri).toFile().readText()
        "kls" -> uri.toKlsURI()?.let { classContentProvider.contentOf(it).second }
            ?: error("Could not find $uri")
        else -> DirectoryManager.HOLLOW_ENGINE.resolve(uri.toString()).readText()
        //else -> error("Unrecognized scheme ${uri.scheme}")
    }
}
