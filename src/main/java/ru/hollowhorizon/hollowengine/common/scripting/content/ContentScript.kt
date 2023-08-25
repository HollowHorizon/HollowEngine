package ru.hollowhorizon.hollowengine.common.scripting.content

import net.minecraft.world.item.crafting.RecipeManager
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.common.scripting.ScriptingCompiler
import ru.hollowhorizon.hc.common.scripting.kotlin.AbstractHollowScriptConfiguration
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.api.valueOrThrow
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies

@KotlinScript(
    displayName = "Content Script",
    fileExtension = "content.kts",
    compilationConfiguration = ContentScriptConfiguration::class
)
abstract class ContentScript(recipeManager: RecipeManager) : ContentScriptBase(recipeManager)

fun runContentScript(recipeManager: RecipeManager, script: File) {
    HollowCore.LOGGER.info("[RecipeScriptCompiler]: loading script \"${script.name}\"")

    val result = ScriptingCompiler.compileFile<ContentScript>(script)

    HollowCore.LOGGER.info("[RecipeScriptCompiler]: Script compiled: \"${result}\"")

    val res = result.execute {
        jvm {
            constructorArgs(recipeManager)
            loadDependencies(false)
        }
    }

    HollowCore.LOGGER.info("[RecipeScriptCompiler]: Script evaluated: \"${res}\"")

    res.reports.forEach {
        HollowCore.LOGGER.info("[ModScriptCompiler]: ${it.render(withStackTrace = true)}")
    }
}

class ContentScriptConfiguration : AbstractHollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hc.client.utils.*"
    )

    baseClass(ContentScriptBase::class)
})