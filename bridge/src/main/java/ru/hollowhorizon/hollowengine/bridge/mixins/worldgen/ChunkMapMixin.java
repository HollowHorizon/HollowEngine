package ru.hollowhorizon.hollowengine.bridge.mixins.worldgen;

import com.mojang.datafixers.DataFixer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bridge.worldgen.StructurePlacementStateExtension;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Shadow
    @Final
    private ChunkGeneratorStructureState chunkGeneratorState;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void hollowengine$attachChunkGenerator(ServerLevel level, LevelStorageSource.LevelStorageAccess storageAccess, DataFixer dataFixer, StructureTemplateManager templateManager, Executor dispatcher, BlockableEventLoop<Runnable> mainThreadExecutor, LightChunkGetter lightChunk, ChunkGenerator generator, ChunkProgressListener progressListener, ChunkStatusUpdateListener statusUpdateListener, Supplier<DimensionDataStorage> dimensionDataStorage, int viewDistance, boolean sync, CallbackInfo callback) {
        ((StructurePlacementStateExtension) chunkGeneratorState).hollowengine$attachChunkGenerator(generator);
    }
}
