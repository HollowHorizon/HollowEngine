package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

@Mixin(Gui.class)
public class GuiMixin {
    //? if >= 1.21 {
    /*@Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void hollowengine$hideScreen(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().shouldHideGui(Minecraft.getInstance().screen)) ci.cancel();
    }
    *///?} else {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    public void hollowengine$hideScreen(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().shouldHideGui(Minecraft.getInstance().screen)) ci.cancel();
    }
    //?}
}
