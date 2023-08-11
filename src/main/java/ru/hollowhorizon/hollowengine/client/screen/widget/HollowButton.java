package ru.hollowhorizon.hollowengine.client.screen.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.widget.ExtendedButton;

public class HollowButton extends ExtendedButton {
    private final OnTooltip tooltip;

    public HollowButton(int x, int y, int width, int height, Component text, OnPress press) {
        this(x, y, width, height, text, press, NO_TOOLTIP);
    }

    public HollowButton(
            int x, int y, int width, int height, Component text, OnPress press, OnTooltip tooltip) {
        super(x, y, width, height, text, press);
        this.tooltip = tooltip;
    }

    @Override
    public void renderToolTip(PoseStack stack, int mouseX, int mouseY) {
        tooltip.onTooltip(this, stack, mouseX, mouseY);
    }
}
