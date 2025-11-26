//? if forge {
package ru.hollowhorizon.hollowengine.common.scripting.deobf

import net.minecraftforge.fml.loading.FMLLoader
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.Mappings
import ru.hollowhorizon.hollowengine.common.scripting.deobf.mappings.remapJars
import ru.hollowhorizon.hollowengine.common.utils.isProduction
import java.io.File
import kotlin.io.path.absolutePathString

object ForgeEnvironmentSetup : EnvironmentSetup {
    override fun setup(
        mappings: Mappings,
        outputDir: File,
    ): List<File> {
        val classpath = forgeClasspath()

        val gameJars = FMLLoader.getLaunchHandler().minecraftPaths.minecraftPaths
            .map { File(it.absolutePathString()) }.filter { it.isFile && it.exists() }

        return if (isProduction) {
            remapJars(
                mappings, gameJars, outputDir,
                from = "intermediary",
                to = "named"
            ).toMutableList() + classpath
        } else {
            classpath.toMutableList() + gameJars
        }
    }

    private fun forgeClasspath(): Set<File> {
        val obfuscatedMC = Regex("""[/\\]versions[/\\][^/\\]+[/\\][^/\\]+\.jar$""")
        return System.getProperty("java.class.path")
            .split(File.pathSeparator)
            .map(::File)
            .filterNot { file -> obfuscatedMC.containsMatchIn(file.absolutePath) }
            .toSet()
    }
}
//?}