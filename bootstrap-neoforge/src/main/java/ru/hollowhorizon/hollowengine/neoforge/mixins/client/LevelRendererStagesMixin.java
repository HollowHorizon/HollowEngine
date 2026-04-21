package ru.hollowhorizon.hollowengine.neoforge.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge;

@Mixin(LevelRenderer.class)
public class LevelRendererStagesMixin {
    @Shadow
    @Nullable
    private Frustum cullingFrustum;

    @Shadow
    private int ticks;

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void onRenderLevelLast(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_LEVEL);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V", shift = At.Shift.AFTER))
    private void afterRenderSky(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_SKY);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;endLastBatch()V", ordinal = 0))
    private void afterRenderEntities(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_ENTITIES);
    }

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=destroyProgress", ordinal = 0))
    private void afterRenderBlockEntities(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_BLOCK_ENTITIES);
    }


    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V", shift = At.Shift.AFTER))
    private void afterRenderParticles(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_PARTICLES);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V", shift = At.Shift.AFTER))
    private void afterRenderWeather(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_WEATHER);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 0, shift = At.Shift.AFTER))
    private void afterRenderSolid(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_SOLID_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 1, shift = At.Shift.AFTER))
    private void afterRenderCutoutMipped(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_CUTOUT_MIPPED_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 2, shift = At.Shift.AFTER))
    private void afterRenderCutout(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_CUTOUT_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 3, shift = At.Shift.AFTER))
    private void afterRenderTranslucentIf(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_TRANSLUCENT_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 5, shift = At.Shift.AFTER))
    private void afterRenderTranslucentElse(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_TRANSLUCENT_BLOCKS);

    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 4, shift = At.Shift.AFTER))
    private void afterRenderTripwireIf(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_TRIPWIRE_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 6, shift = At.Shift.AFTER))
    private void afterRenderTripwireElse(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, deltaTracker.getGameTimeDeltaPartialTick(false), camera, cullingFrustum, RuntimeBridge.RenderLevelStage.AFTER_TRIPWIRE_BLOCKS);
    }
}
