package ru.hollowhorizon.hollowengine.client.ui.ide.recipe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import ru.hollowhorizon.hollowengine.client.ui.ide.recipe.RecipeSlotBinding.Companion.key

enum class RecipeSlotKind { INGREDIENT, RESULT }

/**
 * A place in the recipe a slot shows and fills: how to read it from the JSON and how to write it
 * back. Both work on whatever JSON they are handed, so a binding kept by the palette or the
 * inspector keeps working after the file changes.
 */
class RecipeSlotBinding(
    val id: String,
    val kind: RecipeSlotKind,
    val read: (JsonObject) -> JsonElement?,
    val write: JsonObject.(JsonElement?) -> Unit,
    /** Whether a result here has a count; a furnace's does not. */
    val counted: Boolean = true,
) {
    companion object {
        /** The slot under a top-level [key], the common case. */
        fun key(key: String, kind: RecipeSlotKind, id: String = key, counted: Boolean = true) = RecipeSlotBinding(
            id = id,
            kind = kind,
            read = { json -> json.get(key)?.takeUnless { it.isJsonNull } },
            write = { value -> put(key, value) },
            counted = counted,
        )
    }
}

/**
 * What one open recipe editor remembers: the slot picked for the palette and the inspector, the
 * palette itself, and a change of type waiting to be confirmed.
 */
class RecipeEditorSession {
    /** The recipe as last read; the palette and the inspector act on it from outside the editor. */
    var editing by mutableStateOf<RecipeEditing?>(null)
        internal set

    var selected by mutableStateOf<RecipeSlotBinding?>(null)
        private set

    var pendingType by mutableStateOf<RecipeEditorType?>(null)

    val palette = RecipePaletteState()

    fun select(binding: RecipeSlotBinding?) {
        selected = binding
    }

    /** Puts [pick] into the selected slot. */
    fun pick(pick: RecipePick, add: Boolean) {
        selected?.let { apply(it, pick, add) }
    }

    /**
     * Puts [pick] into [binding]. With [add], an ingredient gains it as one more alternative instead,
     * which only plain items and tags can be; anything else replaces what was there.
     */
    fun apply(binding: RecipeSlotBinding, pick: RecipePick, add: Boolean = false) {
        val recipe = editing ?: return
        recipe.edit {
            val current = binding.read(this)
            val next = when (binding.kind) {
                RecipeSlotKind.INGREDIENT -> pick.ingredient()
                    .let { picked -> if (add) combine(current, picked) else picked.json }

                RecipeSlotKind.RESULT -> {
                    if (pick.tag != null) return@edit
                    pick.result(RecipeResult.of(current)).json
                }
            }
            binding.write(this, next)
        }
    }

    fun write(binding: RecipeSlotBinding, value: JsonElement?) {
        editing?.edit { binding.write(this, value) }
    }

    private fun combine(current: JsonElement?, picked: RecipeIngredient): JsonElement {
        val ingredient = RecipeIngredient.of(current) ?: return picked.json
        val existing = ingredient.alternatives ?: return picked.json
        if (!picked.isPlain) return picked.json
        val added = picked.json.asJsonObject
        if (added in existing) return ingredient.json
        return JsonArray().apply {
            existing.forEach(::add)
            add(added)
        }
    }
}
