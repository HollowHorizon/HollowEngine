package ru.hollowhorizon.hollowengine.common.project.kt.externalsources

import ru.hollowhorizon.hollowengine.common.project.kt.CompilerClassPath
import java.nio.file.Path

class ClassPathSourceArchiveProvider(
    private val cp: CompilerClassPath
) : SourceArchiveProvider {
    override fun fetchSourceArchive(compiledArchive: Path): Path? =
        cp.classPath.firstOrNull { it.compiledJar == compiledArchive }?.sourceJar
}
