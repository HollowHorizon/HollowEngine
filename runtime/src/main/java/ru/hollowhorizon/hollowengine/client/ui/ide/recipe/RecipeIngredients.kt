package ru.hollowhorizon.hollowengine.client.ui.ide.recipe

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimePlatform
import ru.hollowhorizon.hollowengine.common.addons.HollowAddonRuntimeEnvironment

/**
 * One ingredient as written: an item, a tag, a list of alternatives, or a stack with components in
 * the loader's own format. Anything else (another mod's ingredient type) is kept as it is and shown
 * as unknown, never rewritten.
 */
class RecipeIngredient(val json: JsonElement) {
    /** The items it accepts, in order; a tag's are only known once the client has its tags. */
    val stacks: List<ItemStack> by lazy { resolve(json) }

    val label: String by lazy { describe(json) }

    val isTag: Boolean get() = json.isJsonObject && json.asJsonObject.has("tag")

    /** An item or a tag, the only things vanilla lets stand among alternatives. */
    val isPlain: Boolean get() = json.isJsonObject && json.asJsonObject.let { it.has("item") || it.has("tag") }

    /** The plain alternatives it lists, or null when it is not a plain value or a list of them. */
    val alternatives: List<JsonObject>?
        get() = when {
            isPlain -> listOf(json.asJsonObject)
            json.isJsonArray -> json.asJsonArray.map { RecipeIngredient(it) }
                .takeIf { list -> list.all(RecipeIngredient::isPlain) }?.map { it.json.asJsonObject }

            else -> null
        }

    override fun equals(other: Any?): Boolean = other is RecipeIngredient && other.json == json

    override fun hashCode(): Int = json.hashCode()

    companion object {
        fun item(id: String) = RecipeIngredient(JsonObject().apply { addProperty("item", id) })

        fun tag(id: String) = RecipeIngredient(JsonObject().apply { addProperty("tag", id.removePrefix("#")) })

        fun of(element: JsonElement?): RecipeIngredient? =
            element?.takeUnless { it.isJsonNull }?.let(::RecipeIngredient)

        /** [alternatives] as one ingredient: a single one stays an object, several become a list. */
        fun of(alternatives: List<JsonObject>): RecipeIngredient? = when (alternatives.size) {
            0 -> null
            1 -> RecipeIngredient(alternatives.single())
            else -> RecipeIngredient(JsonArray().apply { alternatives.forEach(::add) })
        }

        private fun resolve(element: JsonElement): List<ItemStack> = when {
            element.isJsonArray -> element.asJsonArray.flatMap(::resolve)
            element.isJsonPrimitive -> listOfNotNull(stackOf(element.asString))
            element.isJsonObject -> element.asJsonObject.let { obj ->
                RecipeComponents.resolve(obj, ::resolve) ?: obj.primitive("item")
                    ?.let { listOfNotNull(stackOf(it.asString)) } ?: obj.primitive("tag")?.let { tagItems(it.asString) }
                ?: emptyList()
            }

            else -> emptyList()
        }

        private fun describe(element: JsonElement): String = when {
            element.isJsonArray -> element.asJsonArray.joinToString(" | ", transform = ::describe)
            element.isJsonPrimitive -> element.asString
            element.isJsonObject -> element.asJsonObject.let { obj ->
                RecipeComponents.describe(obj, ::describe) ?: obj.primitive("item")?.asString ?: obj.primitive("tag")
                    ?.let { "#${it.asString}" } ?: obj.primitive("type")?.asString ?: obj.toString()
            }

            else -> element.toString()
        }
    }
}

/** A recipe's output: an item id, a count, and any components. */
class RecipeResult(val json: JsonElement) {
    val id: String
        get() = when {
            json.isJsonPrimitive -> json.asString
            json.isJsonObject -> json.asJsonObject.let { (it.primitive("id") ?: it.primitive("item"))?.asString }
                .orEmpty()

            else -> ""
        }

    val count: Int
        get() = json.takeIf { it.isJsonObject }?.asJsonObject?.primitive("count")
            ?.let { runCatching { it.asInt }.getOrNull() } ?: 1

    val components: JsonObject?
        get() = json.takeIf { it.isJsonObject }?.asJsonObject?.get("components")
            ?.takeIf { it.isJsonObject }?.asJsonObject

    val stack: ItemStack by lazy {
        val stack = stackOf(id) ?: return@lazy ItemStack.EMPTY
        RecipeComponents.apply(stack, components)
        stack.also { it.count = count.coerceIn(1, it.maxStackSize.coerceAtLeast(1)) }
    }

    /**
     * A new item keeps the count but not the components, which belonged to the old one, unless
     * [keepComponents] says the id is only being corrected, as when it is typed in by hand.
     */
    fun withId(next: String, keepComponents: Boolean = false): RecipeResult = RecipeResult(asObject().apply {
        remove("item")
        if (!keepComponents) remove("components")
        addProperty("id", next)
    })

    fun withCount(next: Int): RecipeResult = RecipeResult(asObject().apply {
        put("count", JsonPrimitive(next).takeUnless { next == 1 })
    })

    fun withComponents(next: JsonObject?): RecipeResult = RecipeResult(asObject().apply {
        put("components", next?.takeUnless { it.size() == 0 })
    })

    private fun asObject(): JsonObject = when {
        json.isJsonObject -> json.asJsonObject.deepCopy()
        else -> JsonObject().apply { addProperty("id", id) }
    }

    companion object {
        fun item(id: String) = RecipeResult(JsonObject().apply { addProperty("id", id) })

        fun of(element: JsonElement?): RecipeResult? = element?.takeUnless { it.isJsonNull }?.let(::RecipeResult)
    }
}

/**
 * What goes into a slot: an item or a tag from the palette, or a stack from the inventory together
 * with everything it carries.
 */
class RecipePick(val stack: ItemStack, val tag: String? = null) {
    val id: String get() = tag?.let { "#$it" } ?: BuiltInRegistries.ITEM.getKey(stack.item).toString()

    /** A tag, a plain item, or for a stack with components, the loader's components ingredient. */
    fun ingredient(): RecipeIngredient {
        tag?.let { return RecipeIngredient.tag(it) }
        val item = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val components = RecipeComponents.patchOf(stack) ?: return RecipeIngredient.item(item)
        return RecipeIngredient(RecipeComponents.ingredient(item, components))
    }

    /** The result [previous] becomes with this item: its count kept, its components this stack's. */
    fun result(previous: RecipeResult?): RecipeResult {
        val item = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        val base = previous?.withId(item) ?: RecipeResult.item(item)
        return base.withComponents(RecipeComponents.patchOf(stack))
    }
}

internal fun itemOf(id: String): Item? =
    ResourceLocation.tryParse(id)?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) }

internal fun stackOf(id: String): ItemStack? = itemOf(id)?.let(::ItemStack)

internal fun tagItems(id: String): List<ItemStack> {
    val location = ResourceLocation.tryParse(id.removePrefix("#")) ?: return emptyList()
    return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, location))
        .map { set -> set.map { ItemStack(it.value()) } }.orElse(emptyList())
}

/**
 * Stacks with data components. Vanilla recipes cannot ask for components in an ingredient, so each
 * loader has its own type: NeoForge's `neoforge:components` and Fabric API's `fabric:components`.
 * Results take them the vanilla way, under `components`.
 */
internal object RecipeComponents {
    private const val NeoForgeType = "neoforge:components"
    private const val FabricType = "fabric:components"

    private fun ops(): RegistryOps<JsonElement>? =
        Minecraft.getInstance().level?.registryAccess()?.createSerializationContext(JsonOps.INSTANCE)

    /** The stack's changes from its item's defaults, or null when it has none. */
    fun patchOf(stack: ItemStack): JsonObject? {
        if (stack.componentsPatch.isEmpty) return null
        val ops = ops() ?: return null
        return DataComponentPatch.CODEC.encodeStart(ops, stack.componentsPatch).result().orElse(null)
            ?.takeIf { it.isJsonObject && it.asJsonObject.size() > 0 }?.asJsonObject
    }

    fun ingredient(item: String, components: JsonObject): JsonObject = when (HollowAddonRuntimeEnvironment.platform) {
        RuntimePlatform.NEOFORGE -> JsonObject().apply {
            addProperty("type", NeoForgeType)
            addProperty("items", item)
            // NeoForge matches on components the stack has; a removal ("!id") has no meaning there.
            add("components", JsonObject().apply {
                components.entrySet().filterNot { it.key.startsWith("!") }.forEach { (key, value) -> add(key, value) }
            })
        }

        RuntimePlatform.FABRIC -> JsonObject().apply {
            addProperty("fabric:type", FabricType)
            add("base", JsonObject().apply { addProperty("item", item) })
            add("components", components)
        }
    }

    fun isComponents(json: JsonObject): Boolean =
        json.primitive("type")?.asString == NeoForgeType || json.primitive("fabric:type")?.asString == FabricType

    /** The items [json] asks for, without the components. */
    fun base(json: JsonObject): JsonElement? = when {
        json.primitive("type")?.asString == NeoForgeType -> json.get("items")
        json.primitive("fabric:type")?.asString == FabricType -> json.get("base")
        else -> null
    }

    fun components(json: JsonObject): JsonObject? = json.get("components")?.takeIf { it.isJsonObject }?.asJsonObject

    /** The stacks a components ingredient stands for, or null when [json] is not one. */
    fun resolve(json: JsonObject, resolveBase: (JsonElement) -> List<ItemStack>): List<ItemStack>? {
        if (!isComponents(json)) return null
        val base = base(json) ?: return emptyList()
        // NeoForge's `items` is a holder set: an id, a list of ids or a `#tag`, not an ingredient.
        val stacks =
            if (base.isJsonPrimitive && base.asString.startsWith("#")) tagItems(base.asString) else resolveBase(base)
        return stacks.map { stack -> stack.copy().also { apply(it, components(json)) } }
    }

    fun describe(json: JsonObject, describeBase: (JsonElement) -> String): String? {
        if (!isComponents(json)) return null
        val base = base(json)?.let(describeBase).orEmpty()
        return "$base {${components(json)?.keySet()?.joinToString().orEmpty()}}"
    }

    fun apply(stack: ItemStack, components: JsonObject?) {
        if (components == null || stack.isEmpty) return
        val ops = ops() ?: return
        DataComponentPatch.CODEC.parse(ops, components).result().ifPresent(stack::applyComponents)
    }
}
