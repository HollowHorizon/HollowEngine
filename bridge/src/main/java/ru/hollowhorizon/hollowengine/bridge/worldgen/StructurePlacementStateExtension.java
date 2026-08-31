package ru.hollowhorizon.hollowengine.bridge.worldgen;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.Optional;

public interface StructurePlacementStateExtension {
    void hollowengine$attachChunkGenerator(ChunkGenerator chunkGenerator);

    Optional<ChunkPos> hollowengine$getSurfacePosition(SurfaceStructurePlacement placement);
}
