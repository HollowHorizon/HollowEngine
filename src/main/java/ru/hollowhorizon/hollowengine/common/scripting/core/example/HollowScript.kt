package ru.hollowhorizon.hollowengine.common.scripting.core.example

import kotlinx.coroutines.runBlocking
import ru.hollowhorizon.hollowengine.common.scripting.core.ScriptingCompiler
import ru.hollowhorizon.hollowengine.common.scripting.core.configuration.HollowScriptConfiguration
import kotlin.script.experimental.annotations.KotlinScript

@KotlinScript(
    "HollowScript", "ks.kts", compilationConfiguration = HollowScriptConfiguration::class
)
abstract class HollowScript {
    var name = "Hello World"
}

fun main() = runBlocking {
    val script = ScriptingCompiler.compileText<HollowScript>(
        """
        println(name)
    """.trimIndent()
    )

    script.execute()

    println("Errors: ${script.errors}")
}