package ru.hollowhorizon.hollowengine.client.screen.widget.button;

import com.mojang.blaze3d.vertex.PoseStack;
import kotlin.Unit;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton;
import ru.hollowhorizon.hollowengine.client.render.GUIHelper;

import javax.annotation.Nonnull;

public class IconHollowButton extends BaseButton {
    private final ResourceLocation icon;

    public IconHollowButton(int x, int y, int width, int height, Component text, Runnable onPress, ResourceLocation texture, ResourceLocation icon) {
        super(x, y, width, height, text, (data) -> {
            onPress.run();
            return Unit.INSTANCE;
        }, texture);
        this.icon = icon;
    }

    @Override
    public void render(@Nonnull PoseStack stack, int x, int y, float f) {
        super.render(stack, x, y, f);
        stack.pushPose();
        float color = isCursorAtButton(x, y) ? 0.7F : 1.0F;
        GUIHelper.drawIcon(stack, icon, this.x + getWidth() - getHeight(), this.y, getHeight(), getHeight(), 0.7F, color, color, color, 1.0F);
        stack.popPose();
    }
}
