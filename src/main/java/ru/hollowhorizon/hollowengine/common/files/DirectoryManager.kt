package ru.hollowhorizon.hollowengine.common.files

import net.minecraftforge.fml.loading.FMLPaths
import ru.hollowhorizon.hc.common.scripting.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.content.ContentScript
import ru.hollowhorizon.hollowengine.common.scripting.mod.ModScript
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryScript
import java.io.File

object DirectoryManager {
    val HOLLOW_ENGINE = FMLPaths.GAMEDIR.get().resolve("hollowengine").toFile()
    private val SCRIPTS_DIR = HOLLOW_ENGINE.resolve("scripts").apply {
        if (!exists()) mkdirs()
    }

    @JvmStatic
    fun init() {}

    private fun getScripts() =
        SCRIPTS_DIR.walk().filter { it.path.endsWith(".kts") }.toList()

    fun getAllDialogues() = getScripts().filter { it.path.endsWith(".hsd.kts") }

    fun getAllStoryEvents() = getScripts().filter { it.path.endsWith(".se.kts") }

    fun getModScripts() = getScripts().filter { it.path.endsWith(".mod.kts") }

    fun getContentScripts() = getScripts().filter { it.path.endsWith(".content.kts") }

    fun compileAll() {
        getModScripts().parallelStream().forEach {
            ScriptingCompiler.compileFile<ModScript>(it)
        }
        getAllStoryEvents().parallelStream().forEach {
            ScriptingCompiler.compileFile<StoryScript>(it)
        }
        getContentScripts().parallelStream().forEach {
            ScriptingCompiler.compileFile<ContentScript>(it)
        }
    }

    @JvmStatic
    fun File.toReadablePath(): String {
        val folder = HOLLOW_ENGINE.toPath()
        val path = this.toPath()

        return folder.relativize(path).toString()
    }

    @JvmStatic
    fun String.fromReadablePath(): File {
        return FMLPaths.GAMEDIR.get().resolve("hollowengine").resolve(this).toFile()
    }
}

fun main() {
    val script = File("C:\\Users\\user\\Desktop\\papka_with_papkami\\MyJavaProjects\\HollowEngine\\run\\hollowengine\\scripts\\npc_example.se.kts")
    println(script.toReadablePath())
}