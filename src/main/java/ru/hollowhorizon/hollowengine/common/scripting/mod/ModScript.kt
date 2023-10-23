package ru.hollowhorizon.hollowengine.common.scripting.mod

import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.common.scripting.ScriptingCompiler
import ru.hollowhorizon.hc.common.scripting.kotlin.AbstractHollowScriptConfiguration
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies


@KotlinScript(
    displayName = "Mod Script",
    fileExtension = "mod.kts",
    compilationConfiguration = ModScriptConfiguration::class
)
abstract class ModScript : ModScriptBase()

fun runModScript(script: File) {
    HollowCore.LOGGER.info("[ModScriptCompiler]: loading script \"${script.name}\"")

    val result = ScriptingCompiler.compileFile<ModScript>(script)

    HollowCore.LOGGER.info("[ModScriptCompiler]: Script compiled: \"${result}\"")

    val res = result.execute {
        jvm {
            loadDependencies(false)
        }
    }

    HollowCore.LOGGER.info("[ModScriptCompiler]: Script evaluated: \"${res}\"")

    res.reports.forEach {
        HollowCore.LOGGER.info("[ModScriptCompiler]: ${it.render(withStackTrace = true)}")
    }
}

fun main() {
    runModScript(File("run/hollowengine/scripts/hollow_engine_test.mod.kts"))
}

class ModScriptConfiguration : AbstractHollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.scripting.story.waitForgeEvent",
        "ru.hollowhorizon.hollowengine.common.scripting.story.onForgeEvent",
        "ru.hollowhorizon.hc.client.utils.*"
    )

    baseClass(ModScriptBase::class)
})