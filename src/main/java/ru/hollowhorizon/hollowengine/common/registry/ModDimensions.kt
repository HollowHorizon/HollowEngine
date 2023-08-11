package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.registries.DeferredRegister
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.dimensions.StoryTellerWorldChunkGenerator


object ModDimensions {
    val CHUNK_GENERATORS = DeferredRegister.create(Registry.CHUNK_GENERATOR_REGISTRY, HollowEngine.MODID)
    val DIMENSIONS = DeferredRegister.create(Registry.DIMENSION_REGISTRY, HollowEngine.MODID)

    val VOID_WORLD = ResourceKey.create(Registry.DIMENSION_REGISTRY, ResourceLocation(HollowEngine.MODID, "storyteller_dimension"))

    init {
        CHUNK_GENERATORS.register("storyteller_dimension") { StoryTellerWorldChunkGenerator.CODEC }
    }
}