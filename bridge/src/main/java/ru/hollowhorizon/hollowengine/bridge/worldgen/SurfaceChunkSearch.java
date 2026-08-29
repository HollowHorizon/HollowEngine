package ru.hollowhorizon.hollowengine.bridge.worldgen;

import java.util.Optional;

public final class SurfaceChunkSearch {
    private static final int CHUNK_SIZE = 16;

    private SurfaceChunkSearch() {
    }

    public static Optional<ChunkCoordinates> findNearest(int searchRadius, ChunkPredicate predicate) {
        if (predicate.test(0, 0)) return Optional.of(new ChunkCoordinates(0, 0));

        int maxChunkRadius = searchRadius / CHUNK_SIZE;
        for (int radius = 1; radius <= maxChunkRadius; radius++) {
            for (int z = -radius; z <= radius; z++) {
                if (predicate.test(-radius, z)) {
                    return Optional.of(new ChunkCoordinates(-radius, z));
                }
                if (predicate.test(radius, z)) {
                    return Optional.of(new ChunkCoordinates(radius, z));
                }
            }

            for (int x = -radius + 1; x < radius; x++) {
                if (predicate.test(x, -radius)) {
                    return Optional.of(new ChunkCoordinates(x, -radius));
                }
                if (predicate.test(x, radius)) {
                    return Optional.of(new ChunkCoordinates(x, radius));
                }
            }
        }

        return Optional.empty();
    }

    @FunctionalInterface
    public interface ChunkPredicate {
        boolean test(int chunkX, int chunkZ);
    }

    public record ChunkCoordinates(int x, int z) {
    }
}
