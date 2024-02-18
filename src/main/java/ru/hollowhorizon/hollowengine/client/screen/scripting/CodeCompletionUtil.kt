package ru.hollowhorizon.hollowengine.client.screen.scripting

import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import ru.hollowhorizon.hollowengine.common.scripting.story.StoryScript
import ru.hollowhorizon.kotlinscript.common.scripting.ScriptingCompiler

object CodeCompletionUtil {
    @OptIn(ExperimentalCompilerApi::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val script = ScriptingCompiler.compileText<StoryScript>(
            """
            val npc by NPCEntity.creating {
                model = "npcs/merlin.obj"
            }
        """.trimIndent()
        )

        println(script)
    }
}