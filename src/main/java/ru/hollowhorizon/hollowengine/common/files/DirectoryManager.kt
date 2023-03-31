package ru.hollowhorizon.hollowengine.common.files

import net.minecraftforge.fml.loading.FMLPaths
import java.io.File
import java.lang.IllegalStateException

object DirectoryManager {
    private val SCRIPTS_DIR = FMLPaths.GAMEDIR.get().resolve("hollowengine").toFile().apply {
        if(!exists()) mkdirs()
    }
    val CACHE_DIR = SCRIPTS_DIR.resolve(".cache").apply {
        if(!exists()) mkdirs()
    }

    @JvmStatic
    fun init() {

    }

    fun getScripts(): Collection<File> {
        val dir = FMLPaths.GAMEDIR.get().resolve("hollowengine").toFile()

        if(!dir.exists()) dir.mkdirs()

        val list = arrayListOf<File>()

        collectAllFiles(list, dir) { file ->
            return@collectAllFiles file.path.endsWith(".kts")
        }

        return list
    }

    fun getAllDialogues(): Collection<File> {
        return getScripts().filter { it.path.endsWith(".hsd.kts") }
    }

    fun getAllStoryEvents(): Collection<File> {
        return getScripts().filter { it.path.endsWith(".se.kts") }
    }

    fun findMainScript(): File? {
        val scripts = getScripts().filter { it.endsWith(".main.kts") }

        if (scripts.size > 1) throw IllegalStateException("Main Script can be only one!")

        if(scripts.isEmpty()) return null

        return scripts[0]
    }

    @JvmStatic
    fun File.toReadablePath(): String {
        return this.path.substringAfter(FMLPaths.GAMEDIR.get().resolve("hollowengine").toFile().path+"\\").replace("\\", "/")
    }

    @JvmStatic
    fun String.fromReadablePath(): File {
        return FMLPaths.GAMEDIR.get().resolve("hollowengine").resolve(this).toFile()
    }

    private fun collectAllFiles(list: MutableList<File>, startDir: File, predicate: (File) -> Boolean) {
        startDir.listFiles()?.forEach { file ->
            if(file.isDirectory) {
                collectAllFiles(list, file, predicate)
            } else if(predicate(file)) {
                list.add(file)
            }
        }
    }
}