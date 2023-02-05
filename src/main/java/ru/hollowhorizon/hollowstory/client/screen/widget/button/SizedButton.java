package ru.hollowhorizon.hollowstory.client.screen.widget.button;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import ru.hollowhorizon.hollowstory.client.render.GUIHelper;

public class SizedButton extends Button {
    private final ResourceLocation hoveredTexture;
    private final ResourceLocation texture;
    private int animCounter = 0;

    public SizedButton(int x, int y, int width, int height, ITextComponent text, IPressable onPress, ResourceLocation texture, ResourceLocation hoveredTexture) {
        super(x, y, width, height, text, onPress);
        this.texture = texture;
        this.hoveredTexture = hoveredTexture;
    }

    @Override
    public void render(MatrixStack stack, int p_230430_2_, int p_230430_3_, float p_230430_4_) {
        this.isHovered = p_230430_2_ >= this.x && p_230430_3_ >= this.y && p_230430_2_ < this.x + this.width && p_230430_3_ < this.y + this.height;

        GUIHelper.drawTextInBox(stack, texture, this.getMessage(), this.x, this.y, this.width, 1.0F);
        GUIHelper.drawTextInBox(stack, hoveredTexture, this.getMessage(), this.x, this.y, this.width, animCounter / 15F);

        if (isHovered) {
            if (animCounter < 15) animCounter++;
        } else {
            if (animCounter > 0) animCounter--;
        }

    }
}
