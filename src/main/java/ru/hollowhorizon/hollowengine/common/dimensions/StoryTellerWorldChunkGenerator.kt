package ru.hollowhorizon.hollowengine.common.dimensions

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Registry
import net.minecraft.resources.RegistryOps
import net.minecraft.server.level.WorldGenRegion
import net.minecraft.world.level.LevelHeightAccessor
import net.minecraft.world.level.NoiseColumn
import net.minecraft.world.level.StructureFeatureManager
import net.minecraft.world.level.biome.BiomeManager
import net.minecraft.world.level.biome.BiomeSource
import net.minecraft.world.level.biome.Climate
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.blending.Blender
import net.minecraft.world.level.levelgen.structure.StructureSet
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class StoryTellerWorldChunkGenerator(
    structures: Registry<StructureSet>,
    biomeSource: BiomeSource,
) : ChunkGenerator(structures, Optional.empty(), biomeSource) {

    override fun codec() = CODEC
    override fun withSeed(pSeed: Long): ChunkGenerator = this

    override fun climateSampler(): Climate.Sampler = Climate.empty()

    override fun applyCarvers(
        pLevel: WorldGenRegion,
        pSeed: Long,
        pBiomeManager: BiomeManager,
        pStructureManager: StructureFeatureManager,
        pChunk: ChunkAccess,
        pStep: GenerationStep.Carving
    ) {

    }

    override fun buildSurface(
        pLevel: WorldGenRegion,
        pStructureManager: StructureFeatureManager,
        pChunk: ChunkAccess
    ) {
    }

    override fun spawnOriginalMobs(pLevel: WorldGenRegion) {
    }

    override fun getGenDepth(): Int = 384

    override fun fillFromNoise(
        pExecutor: Executor,
        pBlender: Blender,
        pStructureManager: StructureFeatureManager,
        pChunk: ChunkAccess
    ): CompletableFuture<ChunkAccess> {
        if (pChunk.pos.x == 0 && pChunk.pos.z == 0) {
            val heightmapOcean = pChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG)
            val heightmapSurface = pChunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG)
            pChunk.setBlockState(BlockPos(0, 49, 0), Blocks.DIAMOND_BLOCK.defaultBlockState(), false)
            heightmapOcean.update(0, 49, 0, Blocks.DIAMOND_BLOCK.defaultBlockState())
            heightmapSurface.update(0, 49, 0, Blocks.DIAMOND_BLOCK.defaultBlockState())
        }

        return CompletableFuture.completedFuture(pChunk)
    }

    override fun getSeaLevel(): Int = -63

    override fun getMinY(): Int = 0

    override fun getBaseHeight(
        pX: Int,
        pZ: Int,
        pType: Heightmap.Types,
        pLevel: LevelHeightAccessor
    ): Int {
        return pLevel.minBuildHeight
    }

    override fun getBaseColumn(pX: Int, pZ: Int, pHeight: LevelHeightAccessor): NoiseColumn {
        return NoiseColumn(0, arrayOf())
    }

    override fun addDebugScreenInfo(pInfo: MutableList<String>, pPos: BlockPos) {

    }

    companion object {
        val CODEC: Codec<StoryTellerWorldChunkGenerator> =
            RecordCodecBuilder.create { instance: RecordCodecBuilder.Instance<StoryTellerWorldChunkGenerator> ->
                instance.group(
                    RegistryOps.retrieveRegistry(Registry.STRUCTURE_SET_REGISTRY).forGetter { it.structureSets },
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter { it.biomeSource }
                ).apply(instance, ::StoryTellerWorldChunkGenerator)
            }
    }
}