package ru.hollowhorizon.hollowengine.bootstrap.mixins.fabric;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderPre(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        if (BootstrapRuntimeManager.bridge().onRenderOverlayPre(window, guiGraphics, minecraft.getFrameTime(), RuntimeBridge.OverlayKind.CHAT_PANEL)) {
            ci.cancel();
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderPost(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRenderOverlayPost(minecraft.getWindow(), guiGraphics, minecraft.getFrameTime(), RuntimeBridge.OverlayKind.CHAT_PANEL);
    }
}
