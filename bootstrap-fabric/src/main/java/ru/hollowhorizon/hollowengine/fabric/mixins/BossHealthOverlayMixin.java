package ru.hollowhorizon.hollowengine.fabric.mixins;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.HudLayerIds;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderPre(GuiGraphics guiGraphics, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(window, guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.BOSS_OVERLAY)) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderPost(GuiGraphics guiGraphics, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getTimer().getGameTimeDeltaPartialTick(false), HudLayerIds.BOSS_OVERLAY);
    }
}
