package ru.hollowhorizon.hollowengine.client.screen.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import kotlin.Unit;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import ru.hollowhorizon.hc.client.screens.widget.button.BaseButton;
import ru.hollowhorizon.hc.client.utils.ScissorUtil;

import java.util.List;
import java.util.function.Supplier;

import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;
import static ru.hollowhorizon.hollowengine.client.screen.widget.ModelPreviewWidget.BORDER_WIDTH;
import static ru.hollowhorizon.hollowengine.common.TextHelperKt.literal;

public class ListWidget extends AbstractWidget {
    private final VerticalSliderWidget slider;
    private final List<AbstractWidget> widgets;
    private int minHeight = -1;
    private int currentHeight = 0;

    public ListWidget(int x, int y, int w, int h, List<AbstractWidget> widgets, Supplier<AbstractWidget> widgetSupplier, Component text) {
        super(x, y, w, h, text);

        this.widgets = widgets;

        if (widgetSupplier != null) {

            BaseButton addButton = new BaseButton(-1, -1, 20, 20, literal(""), (b) -> {
                var add_button = widgets.get(widgets.size() - 1);
                widgets.remove(widgets.size() - 1);
                widgets.add(widgetSupplier.get());
                widgets.add(add_button);
                minHeight += -widgets.get(widgets.size() - 2).getHeight() - 10;
                init(true);
                return Unit.INSTANCE;
            }, new ResourceLocation(MODID, "textures/gui/text_field_mini.png"));

            this.widgets.add(addButton);
        }

        this.slider = new VerticalSliderWidget(this.x + this.width - 10, this.y, 10, this.height);
        this.slider.onValueChange(this::setCurrentHeight);

        init(true);
    }

    public void saveValues() {
        for (var widget : this.widgets) {
            if (widget instanceof ISaveable) {
                ((ISaveable) widget).save();
            }
        }
    }

    public void init(boolean updateScroll) {
        if (updateScroll) this.slider.setScroll(currentHeight / (this.minHeight + 0.0F));
        int lastHeight = currentHeight;

        for (var widget : widgets) {
            widget.x = this.x + (this.width - widget.getWidth() - 10) / 2;
            widget.y = this.y + 10 + lastHeight;
            lastHeight += widget.getHeight() + 10;
        }

        if (minHeight == -1) minHeight = -lastHeight + this.height - 10;
    }

    @Override
    public void renderButton(PoseStack stack, int p_230431_2_, int p_230431_3_, float p_230431_4_) {
        fill(stack, x, y, x + width, y + height, 0x66FFFFFF);

        ScissorUtil.INSTANCE.push(
                x + BORDER_WIDTH,
                y + BORDER_WIDTH,
                width - BORDER_WIDTH * 2,
                height - BORDER_WIDTH * 2);
        fillGradient(stack, x, y, x + width, y + height, 0x66000000, 0xCC000000);

        for (var widget : widgets) {
            widget.render(stack, p_230431_2_, p_230431_3_, p_230431_4_);
        }

        ScissorUtil.INSTANCE.pop();

        this.slider.render(stack, p_230431_2_, p_230431_3_, p_230431_4_);
    }

    @Override
    public void playDownSound(SoundManager p_230988_1_) {
    }

    @Override
    public boolean mouseClicked(double p_231044_1_, double p_231044_3_, int p_231044_5_) {
        if (this.slider.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_)) return true;

        for (var widget : this.widgets) {
            widget.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);
        }

        return super.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);
    }

    @Override
    public boolean mouseReleased(double p_231048_1_, double p_231048_3_, int p_231048_5_) {
        System.out.println("released");
        if (this.slider.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_)) return true;

        for (var widget : this.widgets) {
            widget.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);
        }

        return super.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);
    }

    @Override
    public void mouseMoved(double p_212927_1_, double p_212927_3_) {
        for (var widget : this.widgets) {
            widget.mouseMoved(p_212927_1_, p_212927_3_);
        }
        super.mouseMoved(p_212927_1_, p_212927_3_);
    }

    @Override
    public boolean keyPressed(int p_231046_1_, int p_231046_2_, int key) {
        for (var widget : this.widgets) {
            if (widget.keyPressed(p_231046_1_, p_231046_2_, key)) return true;
        }

        if (key == GLFW.GLFW_KEY_SPACE) return false;

        return super.keyPressed(p_231046_1_, p_231046_2_, key);
    }

    @Override
    public boolean keyReleased(int p_223281_1_, int p_223281_2_, int p_223281_3_) {
        for (var widget : this.widgets) {
            if (widget.keyReleased(p_223281_1_, p_223281_2_, p_223281_3_)) return true;
        }
        return super.keyReleased(p_223281_1_, p_223281_2_, p_223281_3_);
    }

    @Override
    public boolean mouseScrolled(double p_231043_1_, double p_231043_3_, double delta) {
        if ((delta > 0 && currentHeight < 0) || (delta < 0 && currentHeight > minHeight)) {
            currentHeight += delta * 5;
            init(true);
        }
        return super.mouseScrolled(p_231043_1_, p_231043_3_, delta);
    }

    @Override
    public boolean charTyped(char p_231042_1_, int p_231042_2_) {
        for (var widget : this.widgets) {
            if (widget.charTyped(p_231042_1_, p_231042_2_)) return true;
        }
        return super.charTyped(p_231042_1_, p_231042_2_);
    }

    public void setCurrentHeight(float modifier) {
        if (this.widgets.size() > 2) {
            this.currentHeight = (int) (minHeight * modifier);
            init(false);
        }
    }

    @Override
    public void updateNarration(NarrationElementOutput pNarrationElementOutput) {

    }
}
