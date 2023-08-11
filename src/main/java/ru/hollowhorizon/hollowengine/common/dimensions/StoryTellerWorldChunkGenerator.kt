package ru.hollowhorizon.hollowengine.common.dimensions

import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.data.BuiltinRegistries
import net.minecraft.server.level.WorldGenRegion
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.NoiseColumn
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.biome.BiomeManager
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.RandomState
import net.minecraft.world.level.levelgen.blending.Blender
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Function


class StoryTellerWorldChunkGenerator(biomeSource: BiomeSource) :
    ChunkGenerator(BuiltinRegistries.STRUCTURE_SETS, Optional.empty(), biomeSource) {
    override fun codec() = CODEC
    override fun applyCarvers(
        pLevel: WorldGenRegion,
        pSeed: Long,
        pRandom: RandomState,
        pBiomeManager: BiomeManager,
        pStructureManager: StructureManager,
        pChunk: ChunkAccess,
        pStep: GenerationStep.Carving
    ) {

    }

    override fun buildSurface(
        pLevel: WorldGenRegion,
        pStructureManager: StructureManager,
        pRandom: RandomState,
        pChunk: ChunkAccess
    ) {
    }

    override fun spawnOriginalMobs(pLevel: WorldGenRegion) {
    }

    override fun getGenDepth(): Int = 384

    override fun fillFromNoise(
        pExecutor: Executor,
        pBlender: Blender,
        pRandom: RandomState,
        pStructureManager: StructureManager,
        pChunk: ChunkAccess
    ): CompletableFuture<ChunkAccess> {
        return CompletableFuture.completedFuture(pChunk)
    }

    override fun getSeaLevel(): Int = -63

    override fun getMinY(): Int = 0

    override fun getBaseHeight(
        pX: Int,
        pZ: Int,
        pType: Heightmap.Types,
        pLevel: LevelHeightAccessor,
        pRandom: RandomState
    ): Int {
        return pLevel.minBuildHeight
    }

    override fun getBaseColumn(pX: Int, pZ: Int, pHeight: LevelHeightAccessor, pRandom: RandomState): NoiseColumn {
        return NoiseColumn(0, arrayOf())
    }

    override fun addDebugScreenInfo(pInfo: MutableList<String>, pRandom: RandomState, pPos: BlockPos) {

    }

    companion object {
        val CODEC = RecordCodecBuilder.create { instance: RecordCodecBuilder.Instance<StoryTellerWorldChunkGenerator> ->
            instance.group(
                BiomeSource.CODEC.fieldOf("biome_source").forGetter { it.biomeSource }
            ).apply(
                instance,
                instance.stable(Function { biomeSource: BiomeSource -> StoryTellerWorldChunkGenerator(biomeSource) })
            )
        }
    }
}