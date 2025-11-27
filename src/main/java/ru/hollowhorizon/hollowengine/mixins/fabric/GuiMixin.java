package ru.hollowhorizon.hollowengine.mixins.fabric;

import com.mojang.blaze3d.platform.Window;
//? if >= 1.21
/*import net.minecraft.client.DeltaTracker;*/
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
import ru.hollowhorizon.hollowengine.client.handlers.TickHandler;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.client.render.GuiOverlay;
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderOverlayEvent;

@Mixin(Gui.class)
public class GuiMixin {
    //? if fabric {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private static ResourceLocation PUMPKIN_BLUR_LOCATION;

    @Shadow
    @Final
    private static ResourceLocation POWDER_SNOW_OUTLINE_LOCATION;

    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void onRenderVingettePre(GuiGraphics guiGraphics, Entity entity, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.VIGNETTE);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderVignette", at = @At("RETURN"))
    private void onRenderVingettePost(GuiGraphics guiGraphics, Entity entity, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.VIGNETTE);
        EventBus.post(event);
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderSpyglassPre(GuiGraphics guiGraphics, float scopeScale, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.SPYGLASS);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("RETURN"))
    private void onRenderSpyglassPost(GuiGraphics guiGraphics, float scopeScale, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.SPYGLASS);
        EventBus.post(event);
    }

    @Inject(method = "renderTextureOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderTextureOverlayPre(GuiGraphics guiGraphics, ResourceLocation shaderLocation, float alpha, CallbackInfo ci) {
        Window window = minecraft.getWindow();

        GuiOverlay overlay = GuiOverlay.HELMET;
        if (shaderLocation == PUMPKIN_BLUR_LOCATION) {
            overlay = GuiOverlay.HELMET;
        } else if (shaderLocation == POWDER_SNOW_OUTLINE_LOCATION) {
            overlay = GuiOverlay.SPYGLASS;
        }

        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), overlay);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderTextureOverlay", at = @At("RETURN"))
    private void onRenderTextureOverlayPost(GuiGraphics guiGraphics, ResourceLocation shaderLocation, float alpha, CallbackInfo ci) {
        GuiOverlay overlay = GuiOverlay.HELMET;
        if (shaderLocation == PUMPKIN_BLUR_LOCATION) {
            overlay = GuiOverlay.HELMET;
        } else if (shaderLocation == POWDER_SNOW_OUTLINE_LOCATION) {
            overlay = GuiOverlay.SPYGLASS;
        }
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), overlay);
        EventBus.post(event);
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderPortalPre(GuiGraphics guiGraphics, float alpha, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.PORTAL);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("RETURN"))
    private void onRenderPortalPost(GuiGraphics guiGraphics, float alpha, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.PORTAL);
        EventBus.post(event);
    }

    //? if >= 1.21 {
    /*@Inject(method = "renderHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbarPre(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.HOTBAR);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }
    *///?} else {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void onRenderHotbarPre(float partialTick, GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, partialTick, GuiOverlay.HOTBAR);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }
    //?}

    //? if >= 1.21 {
    /*@Inject(method = "renderHotbarAndDecorations", at = @At("RETURN"))
    private void onRenderPortalPost(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.HOTBAR);
        EventBus.post(event);
    }
    *///?} else {
    @Inject(method = "renderHotbar", at = @At("RETURN"))
    private void onRenderPortalPost(float partialTick, GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, partialTick, GuiOverlay.HOTBAR);
        EventBus.post(event);
    }
    //?}

    //? if >= 1.21 {
    /*@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshairPre(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.CROSSHAIR);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("RETURN"))
    private void onRenderCrosshairPost(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.CROSSHAIR);
        EventBus.post(event);
    }
    *///?} else {
    
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshairPre(GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.CROSSHAIR);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("RETURN"))
    private void onRenderCrosshairPost(GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.CROSSHAIR);
        EventBus.post(event);
    }
     
    //?}

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void onRenderPlayerHealthPre(GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.PLAYER_HEALTH);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderPlayerHealth", at = @At("RETURN"))
    private void onRenderPlayerHealthPost(GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.PLAYER_HEALTH);
        EventBus.post(event);
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void onRenderVehileHealthPre(GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.MOUNT_HEALTH);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderVehicleHealth", at = @At("RETURN"))
    private void onRenderVehileHealthPost(GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.MOUNT_HEALTH);
        EventBus.post(event);
    }

    @Inject(method = "renderJumpMeter", at = @At("HEAD"), cancellable = true)
    private void onRenderJumpPre(PlayerRideableJumping rideable, GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.JUMP_BAR);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderJumpMeter", at = @At("RETURN"))
    private void onRenderJumpPost(PlayerRideableJumping rideable, GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.JUMP_BAR);
        EventBus.post(event);
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void onRenderXpPre(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.EXPERIENCE_BAR);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderExperienceBar", at = @At("RETURN"))
    private void onRenderXpPost(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.EXPERIENCE_BAR);
        EventBus.post(event);
    }

    @Inject(method = "renderSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void onRenderPortalPre(GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.ITEM_NAME);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderSelectedItemName", at = @At("RETURN"))
    private void onRenderPortalPost(GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.ITEM_NAME);
        EventBus.post(event);
    }

    //? if >= 1.21 {
    /*@Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void onRenderEffectsPre(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.POTION_ICONS);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderEffects", at = @At("RETURN"))
    private void onRenderEffectsPost(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.POTION_ICONS);
        EventBus.post(event);
    }
    *///?} else {
    
    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void onRenderEffectsPre(GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.POTION_ICONS);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "renderEffects", at = @At("RETURN"))
    private void onRenderEffectsPost(GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.POTION_ICONS);
        EventBus.post(event);
    }

     
    //?}


    //?}
}
