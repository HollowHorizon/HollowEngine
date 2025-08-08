package ru.hollowhorizon.hollowengine.common.project.kt

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.project.kt.classpath.ClassPathEntry
import ru.hollowhorizon.hollowengine.common.project.kt.classpath.defaultClassPathResolver
import ru.hollowhorizon.hollowengine.common.project.kt.compiler.Compiler
import ru.hollowhorizon.hollowengine.common.project.kt.util.AsyncExecutor
import java.io.Closeable
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

/**
 * Manages the class path (compiled JARs, etc), the Java source path
 * and the compiler. Note that Kotlin sources are stored in SourcePath.
 */
class CompilerClassPath(
    private val config: CompilerConfiguration,
    private val scriptsConfig: ScriptsConfiguration,
    private val codegenConfig: CodegenConfiguration
) : Closeable {
    val workspaceRoots = mutableSetOf<Path>()

    private val javaSourcePath = mutableSetOf<Path>()
    private val buildScriptClassPath = mutableSetOf<Path>()
    val classPath = mutableSetOf<ClassPathEntry>()
    val outputDirectory: File = Files.createTempDirectory("klsBuildOutput").toFile()
    val javaHome: String? = System.getProperty("java.home", null)

    var compiler = Compiler(
        javaSourcePath,
        classPath.map { it.compiledJar }.toSet(),
        buildScriptClassPath,
        scriptsConfig,
        codegenConfig,
        outputDirectory
    )
        private set

    private val async = AsyncExecutor()

    init {
        compiler.updateConfiguration(config)
    }

    /** Updates and possibly reinstantiates the compiler using new paths. */
    private fun refresh(
        updateClassPath: Boolean = true,
        updateBuildScriptClassPath: Boolean = true,
        updateJavaSourcePath: Boolean = true
    ): Boolean {
        // TODO: Fetch class path and build script class path concurrently (and asynchronously)
        val resolver = defaultClassPathResolver(workspaceRoots)
        var refreshCompiler = updateJavaSourcePath

        if (updateClassPath) {
            val newClassPath = resolver.classpathOrEmpty
            if (newClassPath != classPath) {
                synchronized(classPath) {
                    syncPaths(classPath, newClassPath, "class path") { it.compiledJar }
                }
                refreshCompiler = true
            }

            async.compute {
                val newClassPathWithSources = resolver.classpathWithSources
                synchronized(classPath) {
                    syncPaths(classPath, newClassPathWithSources, "class path with sources") { it.compiledJar }
                }
            }
        }

        if (updateBuildScriptClassPath) {
            val newBuildScriptClassPath = resolver.buildScriptClasspathOrEmpty
            if (newBuildScriptClassPath != buildScriptClassPath) {
                syncPaths(buildScriptClassPath, newBuildScriptClassPath, "build script class path") { it }
                refreshCompiler = true
            }
        }

        if (refreshCompiler) {
            refreshCompiler()
            updateCompilerConfiguration()
        }

        return refreshCompiler
    }

    fun refreshCompiler() {
        compiler.close()
        compiler = Compiler(
            javaSourcePath,
            classPath.map { it.compiledJar }.toSet(),
            buildScriptClassPath,
            scriptsConfig,
            codegenConfig,
            outputDirectory
        )
    }

    /** Synchronizes the given two path sets and logs the differences. */
    private fun <T> syncPaths(dest: MutableSet<T>, new: Set<T>, name: String, toPath: (T) -> Path) {
        val added = new - dest
        val removed = dest - new

        dest.removeAll(removed)
        dest.addAll(added)
    }

    fun updateCompilerConfiguration() {
        compiler.updateConfiguration(config)
    }

    fun addWorkspaceRoot(root: Path): Boolean {
        workspaceRoots.add(root)
        javaSourcePath.addAll(findJavaSourceFiles(root))

        return refresh()
    }

    fun removeWorkspaceRoot(root: Path): Boolean {
        workspaceRoots.remove(root)
        javaSourcePath.removeAll(findJavaSourceFiles(root))

        return refresh()
    }

    fun createdOnDisk(file: Path): Boolean {
        if (isJavaSource(file)) {
            javaSourcePath.add(file)
        }
        return changedOnDisk(file)
    }

    fun deletedOnDisk(file: Path): Boolean {
        if (isJavaSource(file)) {
            javaSourcePath.remove(file)
        }
        return changedOnDisk(file)
    }

    fun changedOnDisk(file: Path): Boolean {
        val buildScript = isBuildScript(file)
        val javaSource = isJavaSource(file)
        if (buildScript || javaSource) {
            return refresh(updateClassPath = buildScript, updateBuildScriptClassPath = false, updateJavaSourcePath = javaSource)
        } else {
            return false
        }
    }

    private fun isJavaSource(file: Path): Boolean = file.fileName.toString().endsWith(".java")

    private fun isBuildScript(file: Path): Boolean = file.fileName.toString().let { it == "pom.xml" || it == "build.gradle" || it == "build.gradle.kts" }

    private fun findJavaSourceFiles(root: Path): Set<Path> {
        val sourceMatcher = FileSystems.getDefault().getPathMatcher("glob:*.java")
        return SourceExclusions(listOf(root), scriptsConfig)
            .walkIncluded()
            .filter { sourceMatcher.matches(it.fileName) }
            .toSet()
    }

    override fun close() {
        compiler.close()
        outputDirectory.delete()
    }
}
