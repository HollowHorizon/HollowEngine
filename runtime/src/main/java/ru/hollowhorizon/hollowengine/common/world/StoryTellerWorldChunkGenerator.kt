package ru.hollowhorizon.hollowengine.common.world

import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.RegistryAccess
import net.minecraft.server.level.WorldGenRegion
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.NoiseColumn
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.biome.BiomeManager
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.blending.Blender
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager
import java.util.concurrent.CompletableFuture

class StoryTellerWorldChunkGenerator(biomeSource: BiomeSource) : ChunkGenerator(biomeSource) {
    override fun codec() = CODEC

    override fun applyCarvers(
        pLevel: WorldGenRegion,
        pSeed: Long,
        pRandom: RandomState,
        pBiomeManager: BiomeManager,
        pStructureManager: StructureManager,
        pChunk: ChunkAccess,
        pStep: GenerationStep.Carving,
    ) {
    }

    override fun buildSurface(
        pLevel: WorldGenRegion,
        pStructureManager: StructureManager,
        pRandom: RandomState,
        pChunk: ChunkAccess,
    ) {
    }

    override fun spawnOriginalMobs(pLevel: WorldGenRegion) {}

    override fun getGenDepth(): Int = 384

    override fun fillFromNoise(
        blender: Blender,
        randomState: RandomState,
        structureManager: StructureManager,
        chunkAccess: ChunkAccess,
    ): CompletableFuture<ChunkAccess> {
        if (chunkAccess.pos.x == 0 && chunkAccess.pos.z == 0) {
            val heightmapOcean = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG)
            val heightmapSurface = chunkAccess.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG)
            chunkAccess.setBlockState(BlockPos(0, 49, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), false)
            heightmapOcean.update(0, 49, 0, Blocks.DIAMOND_BLOCK.defaultBlockState())
            heightmapSurface.update(0, 49, 0, Blocks.DIAMOND_BLOCK.defaultBlockState())
        }

        return CompletableFuture.completedFuture(chunkAccess)
    }

    override fun getSeaLevel(): Int = -63

    override fun getMinY(): Int = 0

    override fun getBaseHeight(
        pX: Int,
        pZ: Int,
        pType: Heightmap.Types,
        pLevel: LevelHeightAccessor,
        pRandom: RandomState,
    ): Int {
        return pLevel.minBuildHeight
    }

    override fun getBaseColumn(pX: Int, pZ: Int, pHeight: LevelHeightAccessor, pRandom: RandomState): NoiseColumn {
        return NoiseColumn(0, arrayOf())
    }

    override fun addDebugScreenInfo(pInfo: MutableList<String>, pRandom: RandomState, pPos: BlockPos) {}

    override fun createStructures(
        registryAccess: RegistryAccess,
        structureState: ChunkGeneratorStructureState,
        structureManager: StructureManager,
        chunk: ChunkAccess,
        structureTemplateManager: StructureTemplateManager,
    ) {
        // Структуры в этом измерении не нужны
    }

    companion object {
        val CODEC: MapCodec<StoryTellerWorldChunkGenerator> =
            RecordCodecBuilder.mapCodec { builder ->
                builder.group(BiomeSource.CODEC.fieldOf("biome_source").forGetter { it.biomeSource })
                    .apply(builder, ::StoryTellerWorldChunkGenerator)
            }

    }
}