package ru.hollowhorizon.hollowengine.common.world.structures.placement;

import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import ru.hollowhorizon.hollowengine.bridge.worldgen.StructurePlacementStateExtension;
import ru.hollowhorizon.hollowengine.bridge.worldgen.SurfaceStructurePlacement;

import java.util.Optional;

public final class SingleSurfaceStructurePlacement extends StructurePlacement implements SurfaceStructurePlacement {
    public static final MapCodec<SingleSurfaceStructurePlacement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codec(instance).apply(instance, SingleSurfaceStructurePlacement::new)
    );

    public static final StructurePlacementType<SingleSurfaceStructurePlacement> TYPE = () -> CODEC;

    private final HolderSet<Biome> preferredBiomes;
    private final int searchRadius;
    private final int surfaceCheckRadius;
    private final int surfaceCheckStep;
    private final int maxSurfaceDeviation;
    private final int biomeYOffset;

    private static Products.P11<
            RecordCodecBuilder.Mu<SingleSurfaceStructurePlacement>,
            Vec3i,
            FrequencyReductionMethod,
            Float,
            Integer,
            Optional<ExclusionZone>,
            HolderSet<Biome>,
            Integer,
            Integer,
            Integer,
            Integer,
            Integer
            > codec(RecordCodecBuilder.Instance<SingleSurfaceStructurePlacement> instance) {
        Products.P5<
                RecordCodecBuilder.Mu<SingleSurfaceStructurePlacement>,
                Vec3i,
                FrequencyReductionMethod,
                Float,
                Integer,
                Optional<ExclusionZone>
                > placement = placementCodec(instance);
        Products.P6<
                RecordCodecBuilder.Mu<SingleSurfaceStructurePlacement>,
                HolderSet<Biome>,
                Integer,
                Integer,
                Integer,
                Integer,
                Integer
                > surface = instance.group(
                RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("preferred_biomes")
                        .forGetter(SingleSurfaceStructurePlacement::preferredBiomes),
                Codec.intRange(0, 30_000_000).fieldOf("search_radius")
                        .forGetter(SingleSurfaceStructurePlacement::searchRadius),
                Codec.intRange(0, 128).optionalFieldOf("surface_check_radius", 0)
                        .forGetter(SingleSurfaceStructurePlacement::surfaceCheckRadius),
                Codec.intRange(1, 128).optionalFieldOf("surface_check_step", 4)
                        .forGetter(SingleSurfaceStructurePlacement::surfaceCheckStep),
                Codec.intRange(0, 512).optionalFieldOf("max_surface_deviation", 4)
                        .forGetter(SingleSurfaceStructurePlacement::maxSurfaceDeviation),
                Codec.intRange(-512, 512).optionalFieldOf("biome_y_offset", 0)
                        .forGetter(SingleSurfaceStructurePlacement::biomeYOffset)
        );
        return new Products.P11<>(
                placement.t1(), placement.t2(), placement.t3(), placement.t4(), placement.t5(),
                surface.t1(), surface.t2(), surface.t3(), surface.t4(), surface.t5(), surface.t6()
        );
    }

    public SingleSurfaceStructurePlacement(
            Vec3i locateOffset,
            FrequencyReductionMethod frequencyReductionMethod,
            float frequency,
            int salt,
            Optional<ExclusionZone> exclusionZone,
            HolderSet<Biome> preferredBiomes,
            int searchRadius,
            int surfaceCheckRadius,
            int surfaceCheckStep,
            int maxSurfaceDeviation,
            int biomeYOffset
    ) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
        this.preferredBiomes = preferredBiomes;
        this.searchRadius = searchRadius;
        this.surfaceCheckRadius = surfaceCheckRadius;
        this.surfaceCheckStep = surfaceCheckStep;
        this.maxSurfaceDeviation = maxSurfaceDeviation;
        this.biomeYOffset = biomeYOffset;
    }

    @Override
    public HolderSet<Biome> preferredBiomes() {
        return preferredBiomes;
    }

    @Override
    public int searchRadius() {
        return searchRadius;
    }

    @Override
    public int surfaceCheckRadius() {
        return surfaceCheckRadius;
    }

    @Override
    public int surfaceCheckStep() {
        return surfaceCheckStep;
    }

    @Override
    public int maxSurfaceDeviation() {
        return maxSurfaceDeviation;
    }

    @Override
    public int biomeYOffset() {
        return biomeYOffset;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        Optional<ChunkPos> position = ((StructurePlacementStateExtension) state)
                .hollowengine$getSurfacePosition(this);
        return position.isPresent()
                && position.get().x == chunkX
                && position.get().z == chunkZ;
    }

    @Override
    public StructurePlacementType<?> type() {
        return TYPE;
    }
}
