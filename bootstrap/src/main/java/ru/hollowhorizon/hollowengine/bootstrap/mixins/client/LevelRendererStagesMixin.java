package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
//? if >= 1.21
/*import net.minecraft.client.DeltaTracker;*/
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge;

@Mixin(LevelRenderer.class)
public class LevelRendererStagesMixin {
    @Shadow
    @Nullable
    private Frustum capturedFrustum;

    @Shadow
    private int ticks;

    //? if >= 1.21 {
    /*@Inject(method = "renderLevel", at = @At("RETURN"))
    private void onRenderLevelLast(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_LEVEL);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V", shift = At.Shift.AFTER))
    private void afterRenderSky(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_SKY);
    }

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=blockentities", ordinal = 0))
    private void afterRenderEntities(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_ENTITIES);
    }

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=destroyProgress", ordinal = 0))
    private void afterRenderBlockEntities(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_BLOCK_ENTITIES);
    }

    //? if fabric {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V", shift = At.Shift.AFTER))
    private void afterRenderParticles(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_PARTICLES);
    }
    //?} else {
    /^@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V", shift = At.Shift.AFTER))
    private void afterRenderParticles(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_PARTICLES);
    }
    ^///?}

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V", shift = At.Shift.AFTER))
    private void afterRenderWeather(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_WEATHER);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 0, shift = At.Shift.AFTER))
    private void afterRenderSolid(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_SOLID_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 1, shift = At.Shift.AFTER))
    private void afterRenderCutoutMipped(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_CUTOUT_MIPPED_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 2, shift = At.Shift.AFTER))
    private void afterRenderCutout(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_CUTOUT_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 3, shift = At.Shift.AFTER))
    private void afterRenderTranslucentIf(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_TRANSLUCENT_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 5, shift = At.Shift.AFTER))
    private void afterRenderTranslucentElse(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_TRANSLUCENT_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 4, shift = At.Shift.AFTER))
    private void afterRenderTripwireIf(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_TRIPWIRE_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", ordinal = 6, shift = At.Shift.AFTER))
    private void afterRenderTripwireElse(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f frustumMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, new PoseStack(), projectionMatrix, ticks, BootstrapRuntimeManager.bridge().currentPartialTick(), camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_TRIPWIRE_BLOCKS);
    }
    *///?} else {

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void onRenderLevelLast(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_LEVEL);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSky(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/Camera;ZLjava/lang/Runnable;)V", shift = At.Shift.AFTER))
    private void afterRenderSky(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_SKY);
    }

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=blockentities", ordinal = 0))
    private void afterRenderEntities(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_ENTITIES);
    }

    @Inject(method = "renderLevel", at = @At(value = "CONSTANT", args = "stringValue=destroyProgress", ordinal = 0))
    private void afterRenderBlockEntities(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_BLOCK_ENTITIES);
    }

    //? if fabric {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;F)V", shift = At.Shift.AFTER))
    private void afterRenderParticles(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_PARTICLES);
    }
    //?} else {
    /*@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/ParticleEngine;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;)V", shift = At.Shift.AFTER))
    private void afterRenderParticles(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_PARTICLES);
    }
    *///?}

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V", shift = At.Shift.AFTER))
    private void afterRenderWeather(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_WEATHER);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V", ordinal = 0, shift = At.Shift.AFTER))
    private void afterRenderSolid(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_SOLID_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V", ordinal = 1, shift = At.Shift.AFTER))
    private void afterRenderCutoutMipped(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_CUTOUT_MIPPED_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V", ordinal = 2, shift = At.Shift.AFTER))
    private void afterRenderCutout(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_CUTOUT_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V", ordinal = 3, shift = At.Shift.AFTER))
    private void afterRenderTranslucentIf(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_TRANSLUCENT_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V", ordinal = 5, shift = At.Shift.AFTER))
    private void afterRenderTranslucentElse(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_TRANSLUCENT_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V", ordinal = 4, shift = At.Shift.AFTER))
    private void afterRenderTripwireIf(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_TRIPWIRE_BLOCKS);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderChunkLayer(Lnet/minecraft/client/renderer/RenderType;Lcom/mojang/blaze3d/vertex/PoseStack;DDDLorg/joml/Matrix4f;)V", ordinal = 6, shift = At.Shift.AFTER))
    private void afterRenderTripwireElse(PoseStack poseStack, float partialTick, long finishNanoTime, boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f projectionMatrix, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderLevelStage((LevelRenderer) (Object) this, poseStack, projectionMatrix, ticks, partialTick, camera, capturedFrustum, RuntimeBridge.RenderLevelStage.AFTER_TRIPWIRE_BLOCKS);
    }
    //?}
}
