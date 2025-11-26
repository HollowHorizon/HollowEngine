package ru.hollowhorizon.hollowengine.common.scripting.deobf

import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.MappingsLoader
import java.io.File
import java.util.jar.JarFile

object CommonEnvironment {
    private val compiler = DirectoryManager.HOLLOW_ENGINE.resolve("HollowEngineCompiler.jar").toFile()
    private val outputDir = DirectoryManager.HOLLOW_ENGINE.resolve(".cache/deobf").toFile()

    fun setup(): Pair<Mappings, MutableList<File>> {
        if (outputDir.exists()) {
            outputDir.deleteRecursively()
            outputDir.mkdirs()
        }

        val mappings = setupMappings()

        val classpath = setupPlatform(mappings, outputDir).toMutableList()

        classpath += ModsEnvironment("hollowengine").setup(mappings, outputDir)

        return mappings to classpath
    }

    private fun setupMappings(): Mappings {
        JarFile(compiler).use { jar ->
            //? if forge {
            val file = jar.getJarEntry("mappings-1.20.1.tsrg")
            //?} else {
            /*val file = jar.getJarEntry("mappings-1.20.1.tiny")
            *///?}
            return MappingsLoader.loadMappings(jar.getInputStream(file))
        }
    }

    //@formatter:off
    fun setupPlatform(mappings: Mappings, outputDir: File): List<File> =
        //? if forge {
        ForgeEnvironmentSetup.setup(mappings, outputDir)
        //?} else {
        /*FabricEnvironmentSetup.setup(mappings, outputDir)
        *///?}
    //@formatter:on

}