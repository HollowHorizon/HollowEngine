package ru.hollowhorizon.hollowstory.client.screen.widget;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import java.util.Map;

public class PlayerIconButton extends IconButton {
    protected final ResourceLocation skinLocation;

    public PlayerIconButton(
            int x, int y, GameProfile profile, IPressable press, ITooltip tooltip, ITextComponent title) {
        super(x, y, 20, 20, BLANK_BUTTON_TEXTURE, 0, 0, 64, 32, 20, press, tooltip, title);
        final SkinManager skinManager = Minecraft.getInstance().getSkinManager();
        final Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map =
                skinManager.getInsecureSkinInformation(profile);
        if (map.containsKey(MinecraftProfileTexture.Type.SKIN)) {
            skinLocation = skinManager.registerTexture(map.get(MinecraftProfileTexture.Type.SKIN), MinecraftProfileTexture.Type.SKIN);
        } else {
            skinLocation = DefaultPlayerSkin.getDefaultSkin(AbstractClientPlayerEntity.createPlayerUUID(profile));
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static void blitFace(MatrixStack poseStack, int x, int y, int size) {
        blit(poseStack, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        blit(poseStack, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void renderButton(MatrixStack poseStack, int mouseX, int mouseY, float partialTicks) {
        TEXTURE_MANAGER.bind(resourceLocation);
        final int yTex = yTexStart + (yDiffText * this.getYImage(this.isHovered()));
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        blit(poseStack, x, y, xTexStart, yTex, width, height, textureWidth, textureHeight);
        TEXTURE_MANAGER.bind(skinLocation);
        RenderSystem.color4f(0.3F, 0.3F, 0.3F, alpha);
        blitFace(poseStack, x + 4, y + 4, 13);
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, alpha);
        blitFace(poseStack, x + 3, y + 3, 13);
        if (this.isHovered()) {
            this.renderToolTip(poseStack, mouseX, mouseY);
        }
    }
}