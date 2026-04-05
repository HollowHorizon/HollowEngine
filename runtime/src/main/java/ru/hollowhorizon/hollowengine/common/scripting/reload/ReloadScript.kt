package ru.hollowhorizon.hollowengine.common.scripting.reload

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeManager
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import ru.hollowhorizon.hollowengine.bridge.mixins.RecipeManagerAccessor
import ru.hollowhorizon.hollowengine.common.compat.util.currentRecipeManagerOrNull
import ru.hollowhorizon.hollowengine.common.utils.rl

abstract class ReloadScript(val context: ReloadScriptContext) {
    val server: MinecraftServer? get() = context.server
    val resources: ResourceManager get() = context.resourceManager

    val crafting: CraftingRecipeEditor get() = context.crafting
    val workbench: CraftingRecipeEditor get() = context.crafting

    val smelting: SmeltingRecipeEditor get() = context.smelting
    val furnace: SmeltingRecipeEditor get() = context.smelting
}

class ReloadScriptContext(
    val server: MinecraftServer?,
    val resourceManager: ResourceManager,
    recipeManager: RecipeManager = currentRecipeManagerOrNull()
        ?: error("RecipeManager is not initialized for reload script context")
) {
    private val editor = RecipeManagerEditor(recipeManager)
    val crafting = CraftingRecipeEditor(editor)
    val smelting = SmeltingRecipeEditor(editor)

    fun flushRecipes() = editor.flush()
}

class CraftingRecipeEditor internal constructor(private val editor: RecipeManagerEditor) {
    fun remove(id: String) = remove(id.rl)

    fun remove(id: ResourceLocation): Boolean = editor.remove(id)

    fun add(id: String, json: String) = add(id.rl, json)

    fun add(id: ResourceLocation, json: String): Recipe<*> = add(id, parseJsonObject(json))

    fun add(id: ResourceLocation, json: JsonObject): Recipe<*> = editor.add(id, json)

    fun shaped(
        id: String,
        result: ItemStack,
        vararg pattern: String,
        keys: Map<Char, Ingredient>,
        count: Int = result.count,
        group: String = "",
    ): Recipe<*> = shaped(id.rl, result, pattern = pattern, keys = keys, count = count, group = group)

    fun shaped(
        id: ResourceLocation,
        result: ItemStack,
        vararg pattern: String,
        keys: Map<Char, Ingredient>,
        count: Int = result.count,
        group: String = "",
    ): Recipe<*> {
        require(pattern.isNotEmpty()) { "Shaped recipe pattern can't be empty" }
        require(keys.isNotEmpty()) { "Shaped recipe keys can't be empty" }

        val json = JsonObject().apply {
            addProperty("type", "minecraft:crafting_shaped")
            addProperty("group", group)
            add("pattern", JsonArray().also { array -> pattern.forEach(array::add) })
            add("key", JsonObject().also { keyObject ->
                keys.forEach { (symbol, ingredient) ->
                    keyObject.add(symbol.toString(), ingredient.toJson())
                }
            })
            add("result", result.toRecipeResultJson(count))
        }

        return editor.add(id, json)
    }

    fun shapeless(
        id: String,
        result: ItemStack,
        vararg ingredients: Ingredient,
        count: Int = result.count,
        group: String = "",
    ): Recipe<*> = shapeless(id.rl, result, ingredients = ingredients, count = count, group = group)

    fun shapeless(
        id: ResourceLocation,
        result: ItemStack,
        vararg ingredients: Ingredient,
        count: Int = result.count,
        group: String = "",
    ): Recipe<*> {
        require(ingredients.isNotEmpty()) { "Shapeless recipe ingredients can't be empty" }

        val json = JsonObject().apply {
            addProperty("type", "minecraft:crafting_shapeless")
            addProperty("group", group)
            add("ingredients", JsonArray().also { array -> ingredients.forEach { array.add(it.toJson()) } })
            add("result", result.toRecipeResultJson(count))
        }

        return editor.add(id, json)
    }
}

class SmeltingRecipeEditor internal constructor(private val editor: RecipeManagerEditor) {
    fun remove(id: String) = remove(id.rl)

    fun remove(id: ResourceLocation): Boolean = editor.remove(id)

    fun add(id: String, json: String) = add(id.rl, json)

    fun add(id: ResourceLocation, json: String): Recipe<*> = add(id, parseJsonObject(json))

    fun add(id: ResourceLocation, json: JsonObject): Recipe<*> = editor.add(id, json)

    fun add(
        id: String,
        ingredient: Ingredient,
        result: ItemStack,
        experience: Float = 0.0f,
        cookingTime: Int = 200,
        group: String = "",
    ): Recipe<*> = add(id.rl, ingredient, result, experience, cookingTime, group)

    fun add(
        id: ResourceLocation,
        ingredient: Ingredient,
        result: ItemStack,
        experience: Float = 0.0f,
        cookingTime: Int = 200,
        group: String = "",
    ): Recipe<*> {
        val json = JsonObject().apply {
            addProperty("type", "minecraft:smelting")
            addProperty("group", group)
            add("ingredient", ingredient.toJson())
            addProperty("result", BuiltInRegistries.ITEM.getKey(result.item).toString())
            addProperty("experience", experience)
            addProperty("cookingtime", cookingTime)
        }

        return editor.add(id, json)
    }
}

class RecipeManagerEditor(private val manager: RecipeManager) {
    private val accessor = manager as RecipeManagerAccessor
    private val recipesByType: MutableMap<RecipeType<*>, MutableMap<ResourceLocation, Recipe<*>>> =
        accessor.`hollowcore$getRecipes`().mapValuesTo(linkedMapOf()) { (_, recipes) -> LinkedHashMap(recipes) }
    private val recipesByName: MutableMap<ResourceLocation, Recipe<*>> =
        LinkedHashMap(accessor.`hollowcore$getByName`())

    fun add(id: ResourceLocation, json: JsonObject): Recipe<*> {
        val recipe = parseRecipe(id, json)
        remove(id)
        recipesByType.getOrPut(recipe.type) { LinkedHashMap() }[id] = recipe
        recipesByName[id] = recipe
        return recipe
    }

    fun remove(id: ResourceLocation): Boolean {
        val removed = recipesByName.remove(id) ?: return false
        recipesByType[removed.type]?.remove(id)
        return true
    }

    fun flush() {
        accessor.`hollowcore$setRecipes`(recipesByType)
        accessor.`hollowcore$setByName`(recipesByName)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseRecipe(id: ResourceLocation, json: JsonObject): Recipe<*> {
        val serializerId = json.get("type")?.asString?.rl
            ?: error("Recipe '$id' must declare a serializer type")
        val serializer = BuiltInRegistries.RECIPE_SERIALIZER.getOptional(serializerId)
            .orElseThrow { IllegalArgumentException("Unknown recipe serializer: $serializerId") }
        return (serializer as RecipeSerializer<Recipe<*>>).fromJson(id, json)
    }
}

fun ingredient(item: String): Ingredient = ingredient(item.rl)

fun ingredient(itemId: ResourceLocation): Ingredient {
    val item = BuiltInRegistries.ITEM.getOptional(itemId)
        .orElseThrow { IllegalArgumentException("Unknown item: $itemId") }
    return Ingredient.of(item)
}

fun ingredient(tag: TagKey<Item>): Ingredient = Ingredient.of(tag)

fun ingredientTag(tag: String): Ingredient {
    val id = tag.removePrefix("#").rl
    return ingredient(TagKey.create(Registries.ITEM, id))
}

private fun ItemStack.toRecipeResultJson(count: Int): JsonObject = JsonObject().apply {
    addProperty("item", BuiltInRegistries.ITEM.getKey(item).toString())
    if (count != 1) addProperty("count", count)
}

private fun parseJsonObject(json: String): JsonObject =
    com.google.gson.JsonParser.parseString(json).asJsonObject
