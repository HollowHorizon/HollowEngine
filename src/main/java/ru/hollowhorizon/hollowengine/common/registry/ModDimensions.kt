package ru.hollowhorizon.hollowengine.common.registry

import net.minecraft.util.RegistryKey
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.common.dimensions.StoryTellerWorldChunkGenerator


object ModDimensions {
    val VOID_WORLD: RegistryKey<World> = RegistryKey.create(Registry.DIMENSION_REGISTRY, "hollowengine:storyteller_dimension".rl)

    init {
        Registry.register(Registry.CHUNK_GENERATOR, "hollowengine:storyteller_dimension".rl, StoryTellerWorldChunkGenerator.CODEC)
    }
}