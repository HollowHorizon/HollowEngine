package ru.hollowhorizon.hollowengine.common.files

import net.minecraftforge.fml.loading.FMLPaths
import java.io.File

object DirectoryManager {
    private val SCRIPTS_DIR = FMLPaths.GAMEDIR.get().resolve("hollowengine/scripts").toFile().apply {
        if (!exists()) mkdirs()
    }

    @JvmStatic
    fun init() {

    }

    fun getScripts() = SCRIPTS_DIR.walk().filter { it.path.endsWith(".kts") }.toList()

    fun getAllDialogues() = getScripts().filter { it.path.endsWith(".hsd.kts") }

    fun getAllStoryEvents() = getScripts().filter { it.path.endsWith(".se.kts") }

    fun getAllModScripts() = getScripts().filter { it.path.endsWith(".mod.kts") }

    fun getModScripts(): Collection<File> = getScripts().filter { it.path.endsWith(".mod.kts") }

    fun findMainScript(): File? {
        val scripts = getScripts().filter { it.endsWith(".main.kts") }

        if (scripts.size > 1) throw IllegalStateException("Main Script can be only one!")

        if (scripts.isEmpty()) return null

        return scripts[0]
    }

    @JvmStatic
    fun File.toReadablePath(): String {
        return this.path.substringAfter(FMLPaths.GAMEDIR.get().resolve("hollowengine").toFile().path + "\\")
            .replace("\\", "/")
    }

    @JvmStatic
    fun String.fromReadablePath(): File {
        return FMLPaths.GAMEDIR.get().resolve("hollowengine").resolve(this).toFile()
    }
}