package ru.hollowhorizon.hollowengine.client.screen.widget;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.StringTextComponent;
import ru.hollowhorizon.hc.client.utils.ScissorUtil;

import java.util.function.Consumer;

import static ru.hollowhorizon.hollowengine.client.screen.widget.ModelPreviewWidget.BORDER_WIDTH;

public class VerticalSliderWidget extends Widget {
    private final int maxHeight;
    private int yHeight;
    private boolean isClicked;
    private Consumer<Float> consumer;

    public VerticalSliderWidget(int x, int y, int w, int h) {
        super(x, y, w, h, new StringTextComponent(""));

        this.maxHeight = this.height - 30;
        yHeight = this.y + 30;
    }

    public int clamp(int value) {
        return MathHelper.clamp(value, this.y + 15, this.y + this.height - 15);
    }

    @Override
    public void render(MatrixStack stack, int p_230430_2_, int mouseY, float p_230430_4_) {
        if (isClicked) {
            yHeight =clamp(mouseY);
            this.consumer.accept(getScroll());
        }

        fill(stack, x, yHeight - 15, x + width, yHeight + 15, 0xFFFFFFFF);

        fill(stack, x, y, x + width, y + height, 0x66FFFFFF);

        ScissorUtil.start(
                x + BORDER_WIDTH,
                y + BORDER_WIDTH,
                width - BORDER_WIDTH * 2,
                height - BORDER_WIDTH * 2);

        fill(stack, x, y, x + width, y + height, 0x33FFFFFF);

        ScissorUtil.stop();
    }

    public float getScroll() {
        return (this.yHeight - this.y - 15) / (this.maxHeight + 0F);
    }

    public void setScroll(float modifier) {
        this.yHeight = clamp(this.y + (int) (this.maxHeight * modifier) + 15);
    }

    public void onValueChange(Consumer<Float> consumer) {
        this.consumer = consumer;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        System.out.println("cli: " + button);
        if (button == 0 && isHovered(mouseX, mouseY)) {
            isClicked = true;
            return true;
        }
        return false;
    }

    public boolean isHovered(double mouseX, double mouseY) {
        return mouseX > this.x && mouseX <= this.x + this.width && mouseY > this.y && mouseY < this.y + this.height;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        System.out.println("rel: " + button);
        if (button == 0) {
            isClicked = false;
            return true;
        }
        return false;
    }
}
