package ru.hollowhorizon.hollowengine.common.dimensions

import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.block.BlockState
import net.minecraft.world.Blockreader
import net.minecraft.world.IBlockReader
import net.minecraft.world.IWorld
import net.minecraft.world.biome.provider.BiomeProvider
import net.minecraft.world.chunk.IChunk
import net.minecraft.world.gen.ChunkGenerator
import net.minecraft.world.gen.Heightmap
import net.minecraft.world.gen.WorldGenRegion
import net.minecraft.world.gen.feature.structure.StructureManager
import net.minecraft.world.gen.settings.DimensionStructuresSettings
import java.util.*
import java.util.function.Function


class StoryTellerWorldChunkGenerator(biomeSource: BiomeProvider) :
    ChunkGenerator(biomeSource, DimensionStructuresSettings(Optional.empty(), emptyMap())) {
    override fun codec() = CODEC

    override fun withSeed(seed: Long): ChunkGenerator {
        return this
    }

    override fun buildSurfaceAndBedrock(pLevel: WorldGenRegion, pChunk: IChunk) {}

    override fun fillFromNoise(p_230352_1_: IWorld, p_230352_2_: StructureManager, p_230352_3_: IChunk) {}

    override fun getBaseHeight(p_222529_1_: Int, p_222529_2_: Int, p_222529_3_: Heightmap.Type): Int = 0

    override fun getBaseColumn(p_230348_1_: Int, p_230348_2_: Int): IBlockReader {
        return Blockreader(arrayOf<BlockState>())
    }

    companion object {
        val CODEC = RecordCodecBuilder.create { instance: RecordCodecBuilder.Instance<StoryTellerWorldChunkGenerator> ->
            instance.group(
                BiomeProvider.CODEC.fieldOf("biome_source").forGetter { it.biomeSource }
            ).apply(
                instance,
                instance.stable(Function { biomeSource: BiomeProvider -> StoryTellerWorldChunkGenerator(biomeSource) })
            )
        }
    }
}