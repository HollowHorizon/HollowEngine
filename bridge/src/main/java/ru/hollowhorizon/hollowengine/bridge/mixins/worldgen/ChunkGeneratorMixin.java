package ru.hollowhorizon.hollowengine.bridge.mixins.worldgen;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bridge.worldgen.StructurePlacementStateExtension;
import ru.hollowhorizon.hollowengine.bridge.worldgen.SurfaceStructurePlacement;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @Unique
    private static BlockPos hollowengine$locateIn(ServerLevel level, ChunkPos chunk, StructurePlacement placement, Holder<Structure> structure, boolean skipKnownStructures) {
        StructureManager structureManager = level.structureManager();
        StructureCheckResult presence = structureManager.checkStructurePresence(chunk, structure.value(), placement, skipKnownStructures);
        if (presence == StructureCheckResult.START_NOT_PRESENT) return null;
        if (!skipKnownStructures && presence == StructureCheckResult.START_PRESENT) {
            return placement.getLocatePos(chunk);
        }

        ChunkAccess access = level.getChunk(chunk.x, chunk.z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = structureManager.getStartForStructure(SectionPos.bottomOf(access), structure.value(), access);
        if (start == null || !start.isValid()) return null;

        if (skipKnownStructures) {
            if (!start.canBeReferenced()) return null;
            structureManager.addReference(start);
        }
        return placement.getLocatePos(start.getChunkPos());
    }

    @Inject(method = "findNearestMapStructure", at = @At("RETURN"), cancellable = true)
    private void hollowengine$locateSurfaceStructures(ServerLevel level, HolderSet<Structure> structures, BlockPos origin, int searchRadius, boolean skipKnownStructures, CallbackInfoReturnable<Pair<BlockPos, Holder<Structure>>> callback) {
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        Pair<BlockPos, Holder<Structure>> nearest = callback.getReturnValue();
        double nearestDistance = nearest == null ? Double.MAX_VALUE : nearest.getFirst().distSqr(origin);

        for (Holder<Structure> structure : structures) {
            for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
                if (!(placement instanceof SurfaceStructurePlacement surface)) continue;

                ChunkPos chunk = ((StructurePlacementStateExtension) state).hollowengine$getSurfacePosition(surface).orElse(null);
                if (chunk == null) continue;

                BlockPos found = hollowengine$locateIn(level, chunk, placement, structure, skipKnownStructures);
                if (found == null) continue;

                double distance = found.distSqr(origin);
                if (distance >= nearestDistance) continue;

                nearest = Pair.of(found, structure);
                nearestDistance = distance;
            }
        }

        if (nearest != callback.getReturnValue()) callback.setReturnValue(nearest);
    }
}
