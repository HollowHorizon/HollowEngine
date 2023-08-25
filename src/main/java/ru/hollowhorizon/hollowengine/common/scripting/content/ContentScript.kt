package ru.hollowhorizon.hollowengine.common.scripting.content

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeType
import ru.hollowhorizon.hc.HollowCore
import ru.hollowhorizon.hc.common.scripting.ScriptingCompiler
import ru.hollowhorizon.hc.common.scripting.kotlin.AbstractHollowScriptConfiguration
import ru.hollowhorizon.hollowengine.mixins.RecipeManagerAccessor
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
abstract class ContentScript(recipes: MutableMap<RecipeType<*>, MutableMap<ResourceLocation, Recipe<*>>>, byName: MutableMap<ResourceLocation, Recipe<*>>) : ContentScriptBase(recipes, byName)

fun runContentScript(recipeManager: RecipeManagerAccessor, script: File) {
    HollowCore.LOGGER.info("[RecipeScriptCompiler]: loading script \"${script.name}\"")

    val result = ScriptingCompiler.compileFile<ContentScript>(script)

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

    recipeManager.`hollowcore$setRecipes`(recipes)
    recipeManager.`hollowcore$setByName`(byName)

    HollowCore.LOGGER.info("[RecipeScriptCompiler]: Script evaluated: \"${res}\"")

    res.reports.forEach {
        HollowCore.LOGGER.error("[ModScriptCompiler]: ${it.render(withStackTrace = true)}")
    }
}

class ContentScriptConfiguration : AbstractHollowScriptConfiguration({
    defaultImports(
        "import ru.hollowhorizon.hollowengine.common.recipes.*",
        "ru.hollowhorizon.hc.client.utils.*"
    )

    baseClass(ContentScriptBase::class)
})