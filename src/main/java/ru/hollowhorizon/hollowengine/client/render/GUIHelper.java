package ru.hollowhorizon.hollowengine.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

import static net.minecraft.client.gui.GuiComponent.blit;
import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;

public class GUIHelper {
    public static final ResourceLocation TEXT_FIELD = new ResourceLocation(MODID, "textures/gui/text_field.png");
    public static final ResourceLocation TEXT_FIELD_LIGHT = new ResourceLocation(MODID, "textures/gui/text_field_light.png");

    public static void drawTextInBox(PoseStack stack, ResourceLocation texture, Component text, int x, int y, int width, float alpha) {
        stack.pushPose();
        TextureManager tm = Minecraft.getInstance().textureManager;
        Font font = Minecraft.getInstance().font;


        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        tm.bindForSetup(texture);
        blit(stack, x, y, 0, 0, 20, 20, 20, 60);


        tm.bindForSetup(texture);
        blit(stack, x + 20, y, 0, 20, width - 40, 20, 20, 60);


        tm.bindForSetup(texture);
        blit(stack, x + width - 20, y, 0, 40, 20, 20, 20, 60);

        font.drawShadow(stack, text, x + width / 2F - font.width(text) / 2F, y + 6, 0xFFFFFF);
        stack.popPose();
    }

    public static void drawTextInBox(PoseStack stack, Component text, int x, int y, int width) {
        drawTextInBox(stack, TEXT_FIELD, text, x, y, width, 1.0F);
    }

    public static void drawIcon(PoseStack stack, ResourceLocation icon, int x, int y, int w, int h, float scale) {
        drawIcon(stack, icon, x, y, w, h, scale, 1.0F);
    }

    public static void drawIcon(PoseStack stack, ResourceLocation icon, int x, int y, int w, int h) {
        drawIcon(stack, icon, x, y, w, h, 1.0F, 1.0F);
    }

    public static void drawIcon(PoseStack stack, ResourceLocation icon, int x, int y, int w, int h, float scale, float alpha) {
        drawIcon(stack, icon, x, y, w, h, scale, 1.0F, 1.0F, 1.0F, alpha);
    }

    public static void drawCentredSizedString(PoseStack stack, Font fr, Component text, int x, int y, int color, float size) {
        stack.pushPose();
        stack.translate(x, y, 0F);
        stack.scale(size, size, size);
        fr.drawShadow(stack, text, -fr.width(text) / 2F, 0, color);
        stack.popPose();
    }

    public static void drawSizedStringWithWidth(PoseStack stack, Font fr, Component text, int x, int y, int width, int color, float size) {
        stack.pushPose();
        stack.translate(x, y, 0F);
        stack.scale(size, size, size);
        List<FormattedCharSequence> lines = fr.split(text, width);

        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            fr.drawShadow(stack, line, -fr.width(line) / 2F, i * fr.lineHeight, color);
        }

        stack.popPose();
    }

    public static void drawIcon(PoseStack stack, ResourceLocation icon, int x, int y, int w, int h, float scale, float r, float g, float b, float alpha) {


        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        RenderSystem.setShaderColor(r, g, b, alpha);
        Minecraft.getInstance().textureManager.bindForSetup(icon);

        blit(stack, (int) (x + w / 2 - scale * w / 2), (int) (y + h / 2 - scale * h / 2), 0, 0, (int) (scale * w), (int) (scale * h), (int) (scale * w), (int) (scale * h));

    }
}
