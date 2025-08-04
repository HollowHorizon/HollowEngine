package ru.hollowhorizon.hollowengine.common.project.kt

import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import java.net.URI
import java.nio.file.Paths
import ru.hollowhorizon.hollowengine.common.project.kt.externalsources.ClassContentProvider
import ru.hollowhorizon.hollowengine.common.project.kt.externalsources.toKlsURI
import java.io.File
import kotlin.io.path.readText

/**
 * Fetches the content of Kotlin files identified by a URI.
 */
class URIContentProvider(
    val classContentProvider: ClassContentProvider
) {
    fun contentOf(uri: File): String = uri.readText()
}
