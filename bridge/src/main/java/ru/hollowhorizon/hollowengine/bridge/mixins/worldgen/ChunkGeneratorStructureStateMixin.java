package ru.hollowhorizon.hollowengine.bridge.mixins.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import ru.hollowhorizon.hollowengine.bridge.worldgen.StructurePlacementStateExtension;
import ru.hollowhorizon.hollowengine.bridge.worldgen.SurfaceChunkSearch;
import ru.hollowhorizon.hollowengine.bridge.worldgen.SurfaceStructurePlacement;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ChunkGeneratorStructureState.class)
public abstract class ChunkGeneratorStructureStateMixin implements StructurePlacementStateExtension {
    @Unique
    private final Map<SurfaceStructurePlacement, Optional<ChunkPos>> hollowengine$surfacePositions = new ConcurrentHashMap<>();
    @Shadow
    @Final
    private RandomState randomState;
    @Shadow
    @Final
    private BiomeSource biomeSource;
    @Unique
    private ChunkGenerator hollowengine$chunkGenerator;

    @Override
    public void hollowengine$attachChunkGenerator(ChunkGenerator chunkGenerator) {
        this.hollowengine$chunkGenerator = Objects.requireNonNull(chunkGenerator);
    }

    @Override
    public Optional<ChunkPos> hollowengine$getSurfacePosition(SurfaceStructurePlacement placement) {
        ChunkGenerator chunkGenerator = Objects.requireNonNull(hollowengine$chunkGenerator, "Chunk generator was not attached to its structure state");
        return hollowengine$surfacePositions.computeIfAbsent(placement, key -> hollowengine$findSurfacePosition(chunkGenerator, key));
    }

    @Unique
    private Optional<ChunkPos> hollowengine$findSurfacePosition(ChunkGenerator chunkGenerator, SurfaceStructurePlacement placement) {
        boolean hasPreferredBiome = placement.preferredBiomes().stream().anyMatch(biomeSource.possibleBiomes()::contains);
        if (!hasPreferredBiome) return Optional.empty();

        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(chunkGenerator.getMinY(), chunkGenerator.getGenDepth());
        return SurfaceChunkSearch.findNearest(placement.searchRadius(), (chunkX, chunkZ) -> hollowengine$isSuitableChunk(chunkGenerator, heightAccessor, placement, chunkX, chunkZ)).map(position -> new ChunkPos(position.x(), position.z()));
    }

    @Unique
    private boolean hollowengine$isSuitableChunk(ChunkGenerator chunkGenerator, LevelHeightAccessor heightAccessor, SurfaceStructurePlacement placement, int chunkX, int chunkZ) {
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        int centerX = chunkPos.getMiddleBlockX();
        int centerZ = chunkPos.getMiddleBlockZ();
        Integer centerHeight = hollowengine$sampleHeight(chunkGenerator, heightAccessor, placement, centerX, centerZ);
        if (centerHeight == null) return false;

        int minimumHeight = centerHeight;
        int maximumHeight = centerHeight;
        int checkRadius = placement.surfaceCheckRadius();
        int checkStep = placement.surfaceCheckStep();

        for (int xOffset = -checkRadius; ; xOffset = Math.min(xOffset + checkStep, checkRadius)) {
            for (int zOffset = -checkRadius; ; zOffset = Math.min(zOffset + checkStep, checkRadius)) {
                if (xOffset != 0 || zOffset != 0) {
                    Integer height = hollowengine$sampleHeight(chunkGenerator, heightAccessor, placement, centerX + xOffset, centerZ + zOffset);
                    if (height == null) return false;
                    minimumHeight = Math.min(minimumHeight, height);
                    maximumHeight = Math.max(maximumHeight, height);
                    if (maximumHeight - minimumHeight > placement.maxSurfaceDeviation()) {
                        return false;
                    }
                }
                if (zOffset == checkRadius) break;
            }
            if (xOffset == checkRadius) break;
        }

        return true;
    }

    @Unique
    private Integer hollowengine$sampleHeight(ChunkGenerator chunkGenerator, LevelHeightAccessor heightAccessor, SurfaceStructurePlacement placement, int blockX, int blockZ) {
        int surfaceHeight = chunkGenerator.getFirstFreeHeight(blockX, blockZ, Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
        Holder<Biome> biome = biomeSource.getNoiseBiome(QuartPos.fromBlock(blockX), QuartPos.fromBlock(surfaceHeight + placement.biomeYOffset()), QuartPos.fromBlock(blockZ), randomState.sampler());
        return placement.preferredBiomes().contains(biome) ? surfaceHeight : null;
    }
}
