package ru.hollowhorizon.hollowengine.mixins.fabric;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.DebugScreenOverlay;
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

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
    //? if fabric && >= 1.21 {
    /*@Shadow @Final private Minecraft minecraft;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderPre(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.CHAT_PANEL);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderPost(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.CHAT_PANEL);
        EventBus.post(event);
    }

    *///?} elif fabric {

    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderPre(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Pre event = new RenderOverlayEvent.Pre(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.CHAT_PANEL);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderPost(GuiGraphics guiGraphics, int tickCount, int mouseX, int mouseY, CallbackInfo ci) {
        Window window = minecraft.getWindow();
        RenderOverlayEvent.Post event = new RenderOverlayEvent.Post(window, guiGraphics, TickHandler.INSTANCE.getPartialTick(), GuiOverlay.CHAT_PANEL);
        EventBus.post(event);
    }

    //?}
}
