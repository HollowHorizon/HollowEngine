package ru.hollowhorizon.hollowengine.fabric.mixins;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.HudLayerIds;

@Mixin(Gui.class)
public class GuiMixin {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private static ResourceLocation PUMPKIN_BLUR_LOCATION;
    @Shadow @Final private static ResourceLocation POWDER_SNOW_OUTLINE_LOCATION;

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void onRenderVignettePre(GuiGraphics guiGraphics, Entity entity, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.VIGNETTE)) ci.cancel();
    }

    @Inject(method = "renderVignette", at = @At("RETURN"))
    private void onRenderVignettePost(GuiGraphics guiGraphics, Entity entity, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.VIGNETTE);
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderSpyglassPre(GuiGraphics guiGraphics, float scopeScale, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.SPYGLASS)) ci.cancel();
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("RETURN"))
    private void onRenderSpyglassPost(GuiGraphics guiGraphics, float scopeScale, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.SPYGLASS);
    }

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderTextureOverlayPre(GuiGraphics guiGraphics, ResourceLocation shaderLocation, float alpha, CallbackInfo ci) {
        String overlay = shaderLocation == POWDER_SNOW_OUTLINE_LOCATION ? HudLayerIds.FROSTBITE : HudLayerIds.HELMET;
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), overlay)) ci.cancel();
    }

    @Inject(method = "renderTextureOverlay", at = @At("RETURN"))
    private void onRenderTextureOverlayPost(GuiGraphics guiGraphics, ResourceLocation shaderLocation, float alpha, CallbackInfo ci) {
        String overlay = shaderLocation == POWDER_SNOW_OUTLINE_LOCATION ? HudLayerIds.FROSTBITE : HudLayerIds.HELMET;
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), overlay);
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderPortalPre(GuiGraphics guiGraphics, float alpha, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.PORTAL)) ci.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("RETURN"))
    private void onRenderPortalPost(GuiGraphics guiGraphics, float alpha, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.PORTAL);
    }

    @Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbarPre(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(minecraft.getWindow(), guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false), HudLayerIds.HOTBAR)) ci.cancel();
    }

    @Inject(method = "renderHotbarAndDecorations", at = @At("RETURN"))
    private void onRenderHotbarPost(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false), HudLayerIds.HOTBAR);
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshairPre(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.CROSSHAIR)) ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("RETURN"))
    private void onRenderCrosshairPost(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.CROSSHAIR);
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void onRenderPlayerHealthPre(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.PLAYER_HEALTH)) ci.cancel();
    }

    @Inject(method = "renderPlayerHealth", at = @At("RETURN"))
    private void onRenderPlayerHealthPost(GuiGraphics guiGraphics, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.PLAYER_HEALTH);
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void onRenderVehicleHealthPre(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.VEHICLE_HEALTH)) ci.cancel();
    }

    @Inject(method = "renderVehicleHealth", at = @At("RETURN"))
    private void onRenderVehicleHealthPost(GuiGraphics guiGraphics, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.VEHICLE_HEALTH);
    }

    @Inject(method = "renderJumpMeter", at = @At("HEAD"), cancellable = true)
    private void onRenderJumpPre(PlayerRideableJumping rideable, GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.JUMP_METER)) ci.cancel();
    }

    @Inject(method = "renderJumpMeter", at = @At("RETURN"))
    private void onRenderJumpPost(PlayerRideableJumping rideable, GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.JUMP_METER);
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void onRenderXpPre(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.EXPERIENCE_BAR)) ci.cancel();
    }

    @Inject(method = "renderExperienceBar", at = @At("RETURN"))
    private void onRenderXpPost(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.EXPERIENCE_BAR);
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void onRenderSelectedItemNamePre(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.SELECTED_ITEM_NAME)) ci.cancel();
    }

    @Inject(method = "renderSelectedItemName", at = @At("RETURN"))
    private void onRenderSelectedItemNamePost(GuiGraphics guiGraphics, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.SELECTED_ITEM_NAME);
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void onRenderEffectsPre(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.EFFECTS)) ci.cancel();
    }

    @Inject(method = "renderEffects", at = @At("RETURN"))
    private void onRenderEffectsPost(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.EFFECTS);
    }
}
