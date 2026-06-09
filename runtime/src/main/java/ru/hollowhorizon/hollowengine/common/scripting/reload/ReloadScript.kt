package ru.hollowhorizon.hollowengine.common.scripting.reload

import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.item.crafting.RecipeManager
import ru.hollowhorizon.hollowengine.common.compat.util.currentRecipeManagerOrNull


abstract class ReloadScript(val context: ReloadScriptContext) {
    val server: MinecraftServer? get() = context.server
    val resources: ResourceManager get() = context.resourceManager
}

class ReloadScriptContext(
    val server: MinecraftServer?,
    val resourceManager: ResourceManager,
    val recipeManager: RecipeManager = currentRecipeManagerOrNull()
        ?: error("RecipeManager is not initialized for reload script context"),
)

