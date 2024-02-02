package ru.hollowhorizon.hollowengine.common.registry.worldgen.structures

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.biome.Biome
import ru.hollowhorizon.hollowengine.HollowEngine.Companion.MODID
import ru.hollowhorizon.hollowengine.common.data.HollowStoryPack


object ModBiomeTags {
    val HOLLOW_STRUCTURE = create("hollow_structure")
    val RUSTIC_TEMPLE = create("rustic_temple")

    private fun create(id: String): TagKey<Biome> {
        return TagKey.create(Registry.BIOME_REGISTRY, ResourceLocation(MODID, "has_structure/$id"))
    }
}