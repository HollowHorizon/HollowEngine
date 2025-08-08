package ru.hollowhorizon.hollowengine.common.project.kt.classpath

import ru.hollowhorizon.hollowengine.HollowEngine
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.PathMatcher

fun defaultClassPathResolver(workspaceRoots: Collection<Path>): ClassPathResolver {
//    val childResolver = WithStdlibResolver(
//        ShellClassPathResolver.global(workspaceRoots.firstOrNull())
//            .or(workspaceRoots.asSequence().flatMap { workspaceResolvers(it) }.joined)
//    ).or(BackupClassPathResolver)

    return MinecraftClassPathResolver()
}

/** Searches the workspace for all files that could provide classpath info. */
private fun workspaceResolvers(workspaceRoot: Path): Sequence<ClassPathResolver> {
    val ignored: List<PathMatcher> = ignoredPathPatterns(workspaceRoot, workspaceRoot.resolve(".gitignore"))
    return folderResolvers(workspaceRoot, ignored).asSequence()
}

/** Searches the folder for all build-files. */
private fun folderResolvers(root: Path, ignored: List<PathMatcher>): Collection<ClassPathResolver> =
    root.toFile()
        .walk()
        .onEnter { file -> ignored.none { it.matches(file.toPath()) } }
        .mapNotNull { asClassPathProvider(it.toPath()) }
        .toList()

/** Tries to read glob patterns from a gitignore. */
private fun ignoredPathPatterns(root: Path, gitignore: Path): List<PathMatcher> =
    gitignore.toFile()
        .takeIf { it.exists() }
        ?.readLines()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() && !it.startsWith("#") }
        ?.map { it.removeSuffix("/") }
        ?.let {
            it + listOf(
                // Patterns that are ignored by default
                ".git"
            )
        }
        ?.mapNotNull {
            try {
                HollowEngine.LOGGER.debug("Adding ignore pattern '{}' from {}", it, gitignore)
                FileSystems.getDefault().getPathMatcher("glob:$root**/$it")
            } catch (e: Exception) {
                HollowEngine.LOGGER.warn("Did not recognize gitignore pattern: '{}' ({})", it, e.message)
                null
            }
        }
        ?: emptyList()

/** Tries to create a classpath resolver from a file using as many sources as possible */
private fun asClassPathProvider(path: Path): ClassPathResolver? =
    MavenClassPathResolver.maybeCreate(path)
        ?: GradleClassPathResolver.maybeCreate(path)
        ?: ShellClassPathResolver.maybeCreate(path)