/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.common.files

import net.minecraftforge.fml.loading.FMLPaths
import ru.hollowhorizon.kotlinscript.common.scripting.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.content.ContentScript
import ru.hollowhorizon.hollowengine.common.scripting.mod.ModScript
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryScript
import java.io.File

object DirectoryManager {
    val HOLLOW_ENGINE = FMLPaths.GAMEDIR.get().resolve("hollowengine").toFile()
    val SCRIPTS_DIR = HOLLOW_ENGINE.resolve("scripts").apply {
        if (!exists()) mkdirs()
    }

    @JvmStatic
    fun init() {}

    private fun getScripts() =
        SCRIPTS_DIR.walk().filter { it.path.endsWith(".kts") }.toList()

    fun getStoryEvents() = getScripts().filter { it.path.endsWith(".se.kts") }

    fun firstJoinEvents() = getStoryEvents().filter { it.readLines().any { it.startsWith("@file:EntryPoint") } }

    fun getModScripts() = getScripts().filter { it.path.endsWith(".mod.kts") }

    fun getContentScripts() = getScripts().filter { it.path.endsWith(".content.kts") }

    fun compileAll() {
        getModScripts().forEach {
            ScriptingCompiler.compileFile<ModScript>(it)
        }
        getStoryEvents().forEach {
            ScriptingCompiler.compileFile<StoryScript>(it)
        }
        getContentScripts().forEach {
            ScriptingCompiler.compileFile<ContentScript>(it)
        }
    }

    @JvmStatic
    fun File.toReadablePath(): String {
        val folder = HOLLOW_ENGINE.toPath()
        val path = this.toPath()

        return folder.relativize(path).toString().replace("\\", "/")
    }

    @JvmStatic
    fun String.fromReadablePath(): File {
        return FMLPaths.GAMEDIR.get().resolve("hollowengine").resolve(this).toFile()
    }
}
