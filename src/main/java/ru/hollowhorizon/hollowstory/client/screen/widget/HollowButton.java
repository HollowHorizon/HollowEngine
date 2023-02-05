package ru.hollowhorizon.hollowstory.client.screen.widget;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.fml.client.gui.widget.ExtendedButton;

public class HollowButton extends ExtendedButton {
    private final ITooltip tooltip;

    public HollowButton(int x, int y, int width, int height, ITextComponent text, IPressable press) {
        this(x, y, width, height, text, press, NO_TOOLTIP);
    }

    public HollowButton(
            int x, int y, int width, int height, ITextComponent text, IPressable press, ITooltip tooltip) {
        super(x, y, width, height, text, press);
        this.tooltip = tooltip;
    }

    @Override
    public void renderToolTip(MatrixStack stack, int mouseX, int mouseY) {
        tooltip.onTooltip(this, stack, mouseX, mouseY);
    }
}
