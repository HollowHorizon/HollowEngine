package ru.hollowhorizon.hollowstory.client.screen.widget.button;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton;
import ru.hollowhorizon.hollowstory.client.render.GUIHelper;

import javax.annotation.Nonnull;

public class IconHollowButton extends BaseButton {
    private final ResourceLocation icon;

    public IconHollowButton(int x, int y, int width, int height, ITextComponent text, IPressable onPress, ResourceLocation texture, ResourceLocation icon) {
        super(x, y, width, height, text, onPress::onPress, texture);
        this.icon = icon;
    }

    @Override
    public void render(@Nonnull MatrixStack stack, int x, int y, float f) {
        super.render(stack, x, y, f);
        stack.pushPose();
        float color = isCursorAtButton(x, y) ? 0.7F : 1.0F;
        GUIHelper.drawIcon(stack, icon, this.x + getWidth() - getHeight(), this.y, getHeight(), getHeight(), 0.7F, color, color, color, 1.0F);
        stack.popPose();
    }
}
