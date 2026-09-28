package ru.hollowhorizon.hollowengine.client.ui.ide.recipe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import ru.hollowhorizon.hollowengine.api.extensions.ExtensionHandle
import ru.hollowhorizon.hollowengine.api.extensions.ExtensionPoints
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.utils.rl

typealias RecipeEditorContent = @Composable (RecipeEditing) -> Unit

/**
 * The visual editor of one recipe type, keyed by the recipe's `type` (its serializer id).
 * [title] is a lang key or plain text for the editor's header; without it the header shows the type.
 * [shape] lets a recipe change to or from this type and keep its ingredients and result; without
 * one, a change of type only rewrites `type` and leaves every other field as it was.
 */
class RecipeEditorType(
    val type: ResourceLocation,
    val title: String? = null,
    val shape: RecipeShape? = null,
    val content: RecipeEditorContent,
)

/** What carries over when a recipe changes type: its inputs in reading order, and its output. */
class RecipeContents(val ingredients: List<JsonElement>, val result: JsonElement?)

/** How a recipe type lays out what [RecipeContents] holds. */
interface RecipeShape {
    fun read(json: JsonObject): RecipeContents

    /**
     * Fills [target], which holds only the new `type`, from [contents]. [previous] is the recipe
     * being converted, for fields both types share, such as `group`.
     */
    fun write(target: JsonObject, contents: RecipeContents, previous: JsonObject)
}

/**
 * Every recipe editor the IDE knows: the engine's vanilla ones, and whatever
 * [RegisterRecipeEditorsEvent] brings on every resource reload.
 */
object RecipeEditors {
    val point = ExtensionPoints.create<RecipeEditorType>("hollowengine:ide/recipe_editors".rl)

    /** What the last [RegisterRecipeEditorsEvent] registered, taken back on the next. */
    private var reloaded: List<ExtensionHandle> = emptyList()

    /** Bumped on every reload, so an open recipe picks up an editor that appeared or changed. */
    var revision by mutableStateOf(0)
        private set

    init {
        VanillaRecipeEditors.all.forEach(::register)
    }

    fun register(editor: RecipeEditorType): ExtensionHandle = point.register(editor.type, editor)

    /** The editor for a recipe whose `type` is [type]; a bare path means the `minecraft` namespace. */
    fun of(type: String): RecipeEditorType? = ResourceLocation.tryParse(type)?.let(point::find)

    /**
     * [recipe] as a recipe of type [to]. Ingredients and the result move over when both types have
     * a shape; what the new type has no place for is dropped. Without both shapes only `type` changes.
     */
    fun convert(recipe: JsonObject, to: RecipeEditorType): JsonObject {
        val from = recipe.get("type")?.takeIf { it.isJsonPrimitive }?.asString?.let(::of)?.shape
        val into = to.shape
        if (from == null || into == null) {
            return recipe.deepCopy().apply { addProperty("type", to.type.toString()) }
        }
        val target = JsonObject().apply { addProperty("type", to.type.toString()) }
        into.write(target, from.read(recipe), recipe)
        return target
    }

    /**
     * Takes back what the previous reload registered and asks again. The vanilla editors go in first,
     * so one a script replaced last time is back if the script no longer replaces it.
     */
    fun reload() {
        reloaded.forEach(ExtensionHandle::close)
        VanillaRecipeEditors.all.forEach(::register)
        val event = RegisterRecipeEditorsEvent.post(RegisterRecipeEditorsEvent())
        reloaded = event.editors.map(::register)
        revision++
    }
}

/**
 * Fires on every resource reload of the client: where a `reload.kts` or an addon adds a visual editor
 * for a modded recipe type, or replaces a vanilla one. The editor gets the same slots, item palette and
 * fields the vanilla editors are built from.
 *
 * ```
 * @SubscribeEvent
 * fun recipeEditors(event: RegisterRecipeEditorsEvent) {
 *     event.register("botania:mana_infusion") { recipe ->
 *         RecipeLayout {
 *             recipe.IngredientSlot("input")
 *             RecipeArrow()
 *             recipe.ResultSlot("output")
 *         }
 *         recipe.IntField("mana", "Mana", default = 1000)
 *     }
 * }
 * ```
 */
class RegisterRecipeEditorsEvent : ClientEvent {
    internal val editors = ArrayList<RecipeEditorType>()

    fun register(editor: RecipeEditorType) {
        editors += editor
    }

    fun register(type: String, title: String? = null, shape: RecipeShape? = null, content: RecipeEditorContent) =
        register(RecipeEditorType(type.rl, title, shape, content))

    companion object : EventHandler<RegisterRecipeEditorsEvent>()
}

/** Asks for the recipe editors again whenever the client reloads its resources. */
object RecipeEditorsReloadListener : ResourceManagerReloadListener {
    override fun onResourceManagerReload(resourceManager: ResourceManager) = RecipeEditors.reload()
}
