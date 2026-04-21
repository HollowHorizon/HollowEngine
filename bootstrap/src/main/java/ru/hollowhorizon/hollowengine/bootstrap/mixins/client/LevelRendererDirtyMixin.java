package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

@Mixin(LevelRenderer.class)
public class LevelRendererDirtyMixin {
    @Inject(method = "allChanged", at = @At("HEAD"), require = 0)
    private void hollowengine$invalidateLocalShadowsOnAllChanged(CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onClientLevelRendererChanged();
    }

    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"), require = 0)
    private void hollowengine$invalidateLocalShadowsOnSectionDirty(int x, int y, int z, boolean rerenderOnMainThread, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onClientLevelRendererChanged();
    }

    @Inject(method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Z)V", at = @At("HEAD"), require = 0)
    private void hollowengine$invalidateLocalShadowsOnBlockDirty(BlockPos pos, boolean important, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onClientLevelRendererChanged();
    }

    @Inject(method = "setBlocksDirty(IIIIII)V", at = @At("HEAD"), require = 0)
    private void hollowengine$invalidateLocalShadowsOnBlocksDirty(int x1, int y1, int z1, int x2, int y2, int z2, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onClientLevelRendererChanged();
    }
}
