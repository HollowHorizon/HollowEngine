package ru.hollowhorizon.hollowengine.bridge.worldgen;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;

public interface SurfaceStructurePlacement {
    HolderSet<Biome> preferredBiomes();

    int searchRadius();

    int surfaceCheckRadius();

    int surfaceCheckStep();

    int maxSurfaceDeviation();

    int biomeYOffset();
}
