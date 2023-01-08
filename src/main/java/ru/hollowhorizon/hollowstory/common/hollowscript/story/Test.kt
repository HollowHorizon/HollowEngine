package ru.hollowhorizon.hollowstory.common.hollowscript.story

import ru.hollowhorizon.hc.common.scripting.HSCompiler
import ru.hollowhorizon.hc.common.scripting.HollowScript
import java.io.File

object Test {

    @JvmStatic
    fun main() {
        val code =
            File("C:\\Users\\user\\Desktop\\papka_with_papkami\\MyJavaProjects\\HollowStory\\src\\main\\resources\\assets\\hollowstory\\hevents\\story_event\\story_event.se.kts").readText()

        val res = HSCompiler().compile<HollowScript>("code", code).execute()

        res.reports.forEach {
            println(it.render())
        }


    }
}