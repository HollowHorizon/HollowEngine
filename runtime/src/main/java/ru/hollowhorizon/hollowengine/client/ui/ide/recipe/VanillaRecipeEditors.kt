package ru.hollowhorizon.hollowengine.client.ui.ide.recipe

import androidx.compose.runtime.Composable
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import ru.hollowhorizon.hollowengine.client.ui.Text
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.utils.rl

/** Editors for the recipe types vanilla reads from data packs; the special crafting ones have no fields. */
internal object VanillaRecipeEditors {
    val all: List<RecipeEditorType> = listOf(
        editor("crafting_shaped", ShapedShape) { ShapedEditor(it) },
        editor("crafting_shapeless", ShapelessShape) { ShapelessEditor(it) },
        cooking("smelting", defaultTime = 200),
        cooking("blasting", defaultTime = 100),
        cooking("smoking", defaultTime = 100),
        cooking("campfire_cooking", defaultTime = 100),
        editor("stonecutting", StonecuttingShape) { StonecuttingEditor(it) },
        editor("smithing_transform", SmithingShape(hasResult = true)) { SmithingEditor(it, result = true) },
        editor("smithing_trim", SmithingShape(hasResult = false)) { SmithingEditor(it, result = false) },
    )

    private fun editor(type: String, shape: RecipeShape, content: RecipeEditorContent) =
        RecipeEditorType("minecraft:$type".rl, recipeLang("type.$type"), shape, content)

    private fun cooking(type: String, defaultTime: Int) = editor(type, CookingShape) { CookingEditor(it, defaultTime) }
}

@Composable
private fun ShapedEditor(recipe: RecipeEditing) {
    if (shapedCells(recipe.json) == null) {
        Text(recipeLang("shaped.too_large").lang, tags = listOf("recipe-note"))
        return
    }
    RecipeLayout {
        RecipeGrid(GridSize, GridSize) { row, column ->
            val index = row * GridSize + column
            recipe.Slot(
                RecipeSlotBinding(
                    id = "grid-$index",
                    kind = RecipeSlotKind.INGREDIENT,
                    read = { json -> shapedCells(json)?.get(index) },
                    write = { value ->
                        shapedCells(this)?.let { cells -> writeShaped(cells.toMutableList().also { it[index] = value }) }
                    },
                ),
            )
        }
        RecipeArrow()
        recipe.ResultSlot()
    }
    recipe.ChoiceField("category", recipeLang("category"), CraftingCategories, default = "misc")
    recipe.StringField("group", recipeLang("group"))
    recipe.ToggleField("show_notification", recipeLang("show_notification"), default = true)
}

@Composable
private fun ShapelessEditor(recipe: RecipeEditing) {
    val count = shapelessIngredients(recipe.json).size
    val rows = maxOf(GridSize, (count + GridSize - 1) / GridSize)
    RecipeLayout {
        RecipeGrid(rows, GridSize) { row, column ->
            val index = row * GridSize + column
            recipe.Slot(
                RecipeSlotBinding(
                    id = "list-$index",
                    kind = RecipeSlotKind.INGREDIENT,
                    read = { json -> shapelessIngredients(json).getOrNull(index) },
                    write = { value ->
                        val current = shapelessIngredients(this).toMutableList<JsonElement?>()
                        while (current.size <= index) current += null
                        current[index] = value
                        add("ingredients", JsonArray().apply { current.filterNotNull().forEach(::add) })
                    },
                ),
            )
        }
        RecipeArrow()
        recipe.ResultSlot()
    }
    recipe.ChoiceField("category", recipeLang("category"), CraftingCategories, default = "misc")
    recipe.StringField("group", recipeLang("group"))
}

@Composable
private fun CookingEditor(recipe: RecipeEditing, defaultTime: Int) {
    RecipeLayout {
        recipe.IngredientSlot("ingredient")
        RecipeFlame()
        RecipeArrow()
        recipe.ResultSlot(count = false)
    }
    recipe.FloatField("experience", recipeLang("experience"))
    recipe.IntField("cookingtime", recipeLang("cooking_time"), default = defaultTime, min = 1)
    recipe.ChoiceField("category", recipeLang("category"), CookingCategories, default = "misc")
    recipe.StringField("group", recipeLang("group"))
}

@Composable
private fun StonecuttingEditor(recipe: RecipeEditing) {
    RecipeLayout {
        recipe.IngredientSlot("ingredient")
        RecipeArrow()
        recipe.ResultSlot()
    }
    recipe.StringField("group", recipeLang("group"))
}

@Composable
private fun SmithingEditor(recipe: RecipeEditing, result: Boolean) {
    RecipeLayout {
        SmithingSlots.forEachIndexed { index, key ->
            if (index > 0) RecipePlus()
            recipe.IngredientSlot(key)
        }
        if (result) {
            RecipeArrow()
            recipe.ResultSlot()
        }
    }
}
