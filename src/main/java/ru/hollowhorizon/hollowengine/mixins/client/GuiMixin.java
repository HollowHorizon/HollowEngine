package ru.hollowhorizon.hollowengine.mixins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.api.HudHideable;

@Mixin(Gui.class)
public class GuiMixin {
    //? if >= 1.21 {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void hideScreen(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof HudHideable hideable && hideable.canHideHud()) ci.cancel();
    }
    *///?} else {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void hideScreen(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof HudHideable hideable && hideable.canHideHud()) ci.cancel();
    }
    //?}
}
