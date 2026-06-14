package ru.hollowhorizon.hollowengine.common.scripting.deobf

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.MappingsLoader
import ru.hollowhorizon.hollowengine.common.utils.isProduction
import ru.hollowhorizon.hollowengine.runtime.bootstrap.HollowEngineRuntimeBootstrap
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

object CommonEnvironment {
    const val RUNTIME_JAR: String = "META-INF/hollowengine/runtime/HollowEngineRuntime.jar"
    const val RUNTIME_SHA: String = "META-INF/hollowengine/runtime/HollowEngineRuntime.sha256"

    private val outputDir = DirectoryManager.HOLLOW_ENGINE.resolve(".cache/deobf").toFile()

    fun setup(compilerJar: File): Pair<Mappings, MutableList<File>> {
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
            outputDir.mkdirs()
        }

        val mappings = setupMappings(compilerJar)

        val classpath = setupPlatform(mappings, outputDir).toMutableList()
        resolveRuntimeJar()?.takeIf(File::isFile)?.let { runtimeJar ->
            if (classpath.none { it.absoluteFile == runtimeJar.absoluteFile }) {
                classpath += runtimeJar
            }
        }

        if (isProduction) classpath += ModsEnvironment("hollowengine").setup(mappings, outputDir)

        return mappings to classpath
    }

    private fun setupMappings(compilerJar: File): Mappings {
        JarFile(compilerJar).use { jar ->
            //? if forge {
            /*val file = jar.getJarEntry("mappings-1.20.1.tsrg")
            *///?} else {
            val file = jar.getJarEntry("mappings-1.20.1.tiny")
            //?}
            return MappingsLoader.loadMappings(jar.getInputStream(file))
        }
    }

    //@formatter:off
    fun setupPlatform(mappings: Mappings, outputDir: File): List<File> =
        //? if forge {
        /*ForgeEnvironmentSetup.setup(mappings, outputDir)
        *///?} else {
        FabricEnvironmentSetup.setup(mappings, outputDir)
        //?}
    //@formatter:on

    private fun resolveRuntimeJar(): File? {
        val cacheDir = File("hollowengine/.cache")
        val classLoader: ClassLoader = HollowEngineRuntimeBootstrap::class.java.classLoader
        val checksum: String?
        classLoader.getResourceAsStream(RUNTIME_SHA).use { shaStream ->
            if (shaStream == null) return null
            BufferedReader(InputStreamReader(shaStream, StandardCharsets.UTF_8)).use { reader ->
                checksum = reader.readLine()
            }
        }

        if (checksum.isNullOrBlank()) return null

        val runtimeDir: Path = cacheDir.toPath().resolve("runtime")
        Files.createDirectories(runtimeDir)
        val target = runtimeDir.resolve("HollowEngineRuntime-" + checksum + ".jar")
        if (Files.exists(target)) return target.toFile()

        return HollowEngine::class.java.protectionDomain?.codeSource?.location
            ?.let { File(it.toURI()) }
            ?.takeIf(File::exists)
    }

}
