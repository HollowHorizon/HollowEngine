package ru.hollowhorizon.hollowengine.client.screen.widget;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import java.util.function.Consumer;

public class HollowTextFieldWidget extends TextFieldWidget {
    private final ResourceLocation texture;
    private String oldSuggestion = "";

    public HollowTextFieldWidget(FontRenderer fr, int x, int y, int w, int h, ITextComponent text, ResourceLocation texture, Consumer<String> consumer) {
        this(fr, x, y, w, h, text, texture);
        this.setResponder(consumer);
    }

    public HollowTextFieldWidget(FontRenderer fr, int x, int y, int w, int h, ITextComponent text, ResourceLocation texture) {
        super(fr, x, y, w, h, text);
        //this.setSuggestion(text.getString());
        this.texture = texture;
        setBordered(false);
    }

    @Override
    public void render(MatrixStack stack, int p_230430_2_, int p_230430_3_, float p_230430_4_) {
        RenderSystem.enableBlend();
        RenderSystem.enableAlphaTest();
        RenderSystem.defaultBlendFunc();
        stack.pushPose();

        Minecraft.getInstance().textureManager.bind(texture);
        blit(stack, x, y, 0, this.isHovered() ? this.height : 0, this.width, this.height, this.width, this.height * 2);

        stack.translate(this.width / 60F, this.height / 2F - 4, 0F);

        super.render(stack, p_230430_2_, p_230430_3_, p_230430_4_);

        stack.popPose();
    }

    @Override
    public boolean keyPressed(int p_231046_1_, int p_231046_2_, int p_231046_3_) {
        return super.keyPressed(p_231046_1_, p_231046_2_, p_231046_3_);
    }

}
