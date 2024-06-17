package ru.hollowhorizon.hollowengine.common.registry

import com.mojang.serialization.MapCodec
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.chunk.ChunkGenerator
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hc.common.registry.RegistryObject
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.world.StoryTellerWorldChunkGenerator

object ModDimensions : HollowRegistry() {
    val STORYTELLER_DIMENSION =
        ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(HollowEngine.MODID, "storyteller_dimension"))
    val STORYTELLER_GENERATOR: RegistryObject<MapCodec<StoryTellerWorldChunkGenerator>> by register(
        "${HollowEngine.MODID}:storyteller_dimension".rl,
        registry = BuiltInRegistries.CHUNK_GENERATOR
    ) {
        StoryTellerWorldChunkGenerator.CODEC
    }
}