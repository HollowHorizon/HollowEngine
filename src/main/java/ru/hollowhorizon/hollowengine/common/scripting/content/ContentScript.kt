package ru.hollowhorizon.hollowengine.common.scripting.content

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hollowengine.mixins.RecipeManagerAccessor
import ru.hollowhorizon.kotlinscript.common.scripting.ScriptingCompiler
import ru.hollowhorizon.kotlinscript.common.scripting.errors
import ru.hollowhorizon.kotlinscript.common.scripting.kotlin.AbstractHollowScriptConfiguration
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.constructorArgs
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.loadDependencies
import kotlin.script.experimental.jvm.util.isError

@KotlinScript(
    displayName = "Content Script",
    fileExtension = "content.kts",
    compilationConfiguration = ContentScriptConfiguration::class
)
abstract class ContentScript(
    recipes: MutableMap<RecipeType<*>, MutableMap<ResourceLocation, Recipe<*>>>,
    byName: MutableMap<ResourceLocation, Recipe<*>>
) : ContentScriptBase(recipes, byName)

fun runContentScript(recipeManager: RecipeManagerAccessor, script: File) {
    HollowCore.LOGGER.info("[ContentScriptCompiler]: loading script \"${script.name}\"")

    val result = ScriptingCompiler.compileFile<ContentScript>(script)

    val creativePlayers = ServerLifecycleHooks.getCurrentServer()?.playerList?.players?.filter { it.abilities.instabuild }

    result.errors?.let { errors ->
        errors.forEach { error ->
            creativePlayers?.forEach {
                it.sendSystemMessage("§c[ERROR]§r $error".mcText)
            } ?: HollowCore.LOGGER.info("[ContentScriptCompiler]: $error")
        }
        return
    }

    HollowCore.LOGGER.info("[RecipeScriptCompiler]: Script compiled: \"${result}\"")

    val recipes = recipeManager.`hollowcore$getRecipes`().toMutableMap()
    val byName = recipeManager.`hollowcore$getByName`().toMutableMap()

    recipes.keys.forEach {
        recipes[it] = recipes[it]?.toMutableMap() ?: hashMapOf()
    }

    val res = result.execute {
        jvm {
            constructorArgs(recipes, byName)
            loadDependencies(false)
        }
    }

    if(res.isError()) {
        (res as ResultWithDiagnostics.Failure).errors().let { errors ->
            errors.forEach { error ->
                creativePlayers?.forEach {
                    it.sendSystemMessage("§c[ERROR]§r $error".mcText)
                } ?: HollowCore.LOGGER.info("[ModScriptCompiler]: $error")
            }
            return
        }
    }

    recipeManager.`hollowcore$setRecipes`(recipes)
    recipeManager.`hollowcore$setByName`(byName)

    HollowCore.LOGGER.info("[RecipeScriptCompiler]: Script evaluated: \"${res}\"")
}

class ContentScriptConfiguration : AbstractHollowScriptConfiguration({
    defaultImports(
        "ru.hollowhorizon.hollowengine.common.recipes.*",
        "ru.hollowhorizon.hollowengine.common.scripting.*",
        "ru.hollowhorizon.hc.client.utils.*"
    )

    baseClass(ContentScriptBase::class)
})
