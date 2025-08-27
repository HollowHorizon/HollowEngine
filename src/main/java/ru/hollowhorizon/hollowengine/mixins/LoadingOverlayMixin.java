package ru.hollowhorizon.hollowengine.mixins;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.client.utils.ForgeKotlinClientKt;
import ru.hollowhorizon.hollowengine.common.utils.ForgeKotlinKt;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow @Final private boolean fadeIn;
    @Shadow private long fadeInStart;
    @Shadow private long fadeOutStart;
    @Shadow @Final private Minecraft minecraft;

    @Shadow
    private static int replaceAlpha(int color, int alpha) {
        return 0;
    }

    private static IntSupplier BRAND_BACKGROUND = () -> FastColor.ARGB32.color(255, 6, 8, 19);
    @Shadow private float currentProgress;

    @Shadow protected abstract void drawProgressBar(GuiGraphics guiGraphics, int minX, int minY, int maxX, int maxY, float partialTick);

    @Shadow @Final private ReloadInstance reload;
    @Shadow @Final private Consumer<Optional<Throwable>> onFinish;
    private static final ResourceLocation HOLLOWENGINE_LOGO_LOCATION = new ResourceLocation("hollowengine:textures/gui/bg/loading.png");


    @Inject(method = "registerTextures", at =@At("TAIL"))
    private static void onRegisterTextures(Minecraft minecraft, CallbackInfo ci) {
        try {
            minecraft.getTextureManager().register(HOLLOWENGINE_LOGO_LOCATION, new DynamicTexture(NativeImage.read(ForgeKotlinClientKt.getStream(HOLLOWENGINE_LOGO_LOCATION))));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        int guiWidth = guiGraphics.guiWidth();
        int guiHeight = guiGraphics.guiHeight();
        long l = Util.getMillis();
        if (this.fadeIn && this.fadeInStart == -1L) {
            this.fadeInStart = l;
        }

        float f = this.fadeOutStart > -1L ? (float)(l - this.fadeOutStart) / 1000.0F : -1.0F;
        float g = this.fadeInStart > -1L ? (float)(l - this.fadeInStart) / 500.0F : -1.0F;
        float h;
        int k;
        if (f >= 1.0F) {
            if (this.minecraft.screen != null) {
                this.minecraft.screen.render(guiGraphics, 0, 0, partialTick);
            }

            k = Mth.ceil((1.0F - Mth.clamp(f - 1.0F, 0.0F, 1.0F)) * 255.0F);
            guiGraphics.fill(RenderType.guiOverlay(), 0, 0, guiWidth / 2 - guiHeight / 2, guiHeight, replaceAlpha(BRAND_BACKGROUND.getAsInt(), k));
            guiGraphics.fill(RenderType.guiOverlay(), guiWidth / 2 + guiHeight / 2, 0, guiWidth, guiHeight, replaceAlpha(BRAND_BACKGROUND.getAsInt(), k));
            h = 1.0F - Mth.clamp(f - 1.0F, 0.0F, 1.0F);
        } else if (this.fadeIn) {
            if (this.minecraft.screen != null && g < 1.0F) {
                this.minecraft.screen.render(guiGraphics, mouseX, mouseY, partialTick);
            }

            k = Mth.ceil(Mth.clamp((double)g, 0.15, 1.0) * 255.0);
            guiGraphics.fill(RenderType.guiOverlay(), 0, 0, guiWidth / 2 - guiHeight / 2, guiHeight, replaceAlpha(BRAND_BACKGROUND.getAsInt(), k));
            guiGraphics.fill(RenderType.guiOverlay(), guiWidth / 2 + guiHeight / 2, 0, guiWidth, guiHeight, replaceAlpha(BRAND_BACKGROUND.getAsInt(), k));
            h = Mth.clamp(g, 0.0F, 1.0F);
        } else {
            k = BRAND_BACKGROUND.getAsInt();
            float m = (float)(k >> 16 & 255) / 255.0F;
            float n = (float)(k >> 8 & 255) / 255.0F;
            float o = (float)(k & 255) / 255.0F;
            guiGraphics.fill(RenderType.guiOverlay(), 0, 0, guiWidth / 2 - guiHeight / 2, guiHeight, BRAND_BACKGROUND.getAsInt());
            guiGraphics.fill(RenderType.guiOverlay(), guiWidth / 2 + guiHeight / 2, 0, guiWidth, guiHeight, BRAND_BACKGROUND.getAsInt());
            h = 1.0F;
        }

        var width = (int)((double)guiGraphics.guiWidth() * 0.5);
        double d = Math.min((double)guiGraphics.guiWidth() * 0.75, guiGraphics.guiHeight()) * 0.25;
        double e = d * 4.0;
        int r = (int)(e * 0.5);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, h);
        guiGraphics.blit(HOLLOWENGINE_LOGO_LOCATION, width - (guiGraphics.guiHeight() / 2), 0, 0, 0, guiGraphics.guiHeight(), guiGraphics.guiHeight(), guiGraphics.guiHeight(), guiGraphics.guiHeight());
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        int s = (int)((double)guiGraphics.guiHeight() * 0.8325);
        float t = this.reload.getActualProgress();
        this.currentProgress = Mth.clamp(this.currentProgress * 0.95F + t * 0.050000012F, 0.0F, 1.0F);
        if (f < 1.0F) {
            this.drawProgressBar(guiGraphics, guiWidth / 2 - r, s - 5, guiWidth / 2 + r, s + 5, 1.0F - Mth.clamp(f, 0.0F, 1.0F));
        }

        if (f >= 2.0F) {
            this.minecraft.setOverlay((Overlay)null);
        }

        if (this.fadeOutStart == -1L && this.reload.isDone() && (!this.fadeIn || g >= 2.0F)) {
            try {
                this.reload.checkExceptions();
                this.onFinish.accept(Optional.empty());
            } catch (Throwable var23) {
                Throwable throwable = var23;
                this.onFinish.accept(Optional.of(throwable));
            }

            this.fadeOutStart = Util.getMillis();
            if (this.minecraft.screen != null) {
                this.minecraft.screen.init(this.minecraft, guiGraphics.guiWidth(), guiGraphics.guiHeight());
            }
        }
        ci.cancel();
    }
}
