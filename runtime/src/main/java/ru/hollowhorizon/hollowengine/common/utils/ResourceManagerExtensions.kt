package ru.hollowhorizon.hollowengine.common.utils

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.VanillaPackResources
import net.minecraft.server.packs.resources.ResourceManager

fun ResourceManager.walk(path: String, suffix: String = ""): Set<ResourceLocation> =
    this.listResources(path) { location ->
        // Если суффикс пуст, принимаем все.
        // Иначе проверяем, оканчивается ли путь ресурса на суффикс.
        suffix.isEmpty() || location.path.endsWith(suffix)
    }.keys

data class PackResourceEntry(
    val location: ResourceLocation,
    val sourcePackId: String,
)

/**
 * Lists resources pack-by-pack because the vanilla pack rejects the empty path accepted by mod packs.
 * Later packs replace earlier entries, matching ResourceManager's normal priority order.
 */
fun ResourceManager.listPackResources(type: PackType, namespace: String? = null): List<PackResourceEntry> {
    val resources = linkedMapOf<ResourceLocation, PackResourceEntry>()
    listPacks().use { packs ->
        packs.forEach { pack ->
            val availableNamespaces = pack.getNamespaces(type)
            val namespaces = namespace?.let(::setOf) ?: availableNamespaces
            namespaces.asSequence()
                .filter { it in availableNamespaces }
                .flatMap { currentNamespace -> pack.listResourceLocations(type, currentNamespace).asSequence() }
                .forEach { location -> resources[location] = PackResourceEntry(location, pack.packId()) }
        }
    }
    return resources.values.toList()
}

internal fun PackResources.listResourceLocations(type: PackType, namespace: String): Set<ResourceLocation> {
    val locations = linkedSetOf<ResourceLocation>()
    if (supportsEmptyResourcePath()) collectResourceLocations(type, namespace, "", locations)
    if (locations.isEmpty()) {
        resourceRoots(type).forEach { root -> collectResourceLocations(type, namespace, root, locations) }
    }
    return locations
}

private fun PackResources.collectResourceLocations(
    type: PackType,
    namespace: String,
    path: String,
    output: MutableSet<ResourceLocation>,
) {
    runCatching {
        listResources(type, namespace, path) { location, _ -> output += location }
    }
}

private fun PackResources.supportsEmptyResourcePath(): Boolean = this !is VanillaPackResources

private fun resourceRoots(type: PackType): List<String> = when (type) {
    PackType.CLIENT_RESOURCES -> CLIENT_RESOURCE_ROOTS
    PackType.SERVER_DATA -> SERVER_RESOURCE_ROOTS
}

private val CLIENT_RESOURCE_ROOTS = listOf(
    "atlases",
    "audio",
    "blockstates",
    "equipment",
    "font",
    "fonts",
    "lang",
    "models",
    "particles",
    "post_effect",
    "resourcepacks",
    "shaders",
    "sounds",
    "sounds.json",
    "texts",
    "textures",
    "ui",
)

private val SERVER_RESOURCE_ROOTS = listOf(
    "advancement",
    "advancements",
    "banner_pattern",
    "cat_variant",
    "chat_type",
    "damage_type",
    "dimension",
    "dimension_type",
    "enchantment",
    "enchantment_provider",
    "flat_level_generator_preset",
    "frog_variant",
    "function",
    "functions",
    "instrument",
    "item_modifier",
    "jukebox_song",
    "loot_table",
    "loot_tables",
    "painting_variant",
    "predicate",
    "predicates",
    "recipe",
    "recipes",
    "structure",
    "structures",
    "tags",
    "trim_material",
    "trim_pattern",
    "wolf_variant",
    "worldgen",
)
