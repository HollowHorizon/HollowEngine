package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import ru.hollowhorizon.hc.common.utils.rl
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.world.StoryTellerWorldChunkGenerator

object ModDimensions : HollowRegistry() {
    val STORYTELLER_DIMENSION =
        ResourceKey.create(Registries.DIMENSION, "${HollowEngine.MODID}:storyteller_dimension".rl)
    val STORYTELLER_GENERATOR by register(
        "${HollowEngine.MODID}:storyteller_dimension".rl,
        registry = BuiltInRegistries.CHUNK_GENERATOR
    ) {
        StoryTellerWorldChunkGenerator.CODEC
    }
}