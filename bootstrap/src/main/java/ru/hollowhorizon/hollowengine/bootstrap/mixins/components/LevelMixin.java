package ru.hollowhorizon.hollowengine.bootstrap.mixins.components;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

import java.util.function.Supplier;

@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(WritableLevelData levelData, ResourceKey<?> dimension, RegistryAccess registryAccess, Holder<?> dimensionTypeRegistration, Supplier<?> profiler, boolean isClientSide, boolean isDebug, long biomeZoomSeed, int maxChainedNeighborUpdates, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onLevelCreated((Level) (Object) this);
    }

    @Inject(method = "updateNeighborsAt", at = @At("HEAD"))
    private void onUpdateNeighbors(BlockPos pos, Block block, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onLevelUpdateNeighbors((Level) (Object) this, pos);
    }

    @Inject(method = "tickBlockEntities", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onLevelTickBlockEntities((Level) (Object) this);
    }

    @Inject(method = "close", at = @At("TAIL"), remap = false)
    private void onClose(CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onLevelClosed((Level) (Object) this);
    }
}
