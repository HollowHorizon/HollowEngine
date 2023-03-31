package ru.hollowhorizon.hollowengine.client.screen.widget;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import ru.hollowhorizon.hollowengine.client.render.GUIHelper;
import ru.hollowhorizon.hollowengine.client.screen.widget.button.IconHollowButton;

import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;

public class SMDAnimationWidget extends Widget implements ISaveable {
    private final HollowTextFieldWidget animNameWidget;
    private final ResourceFieldWidget animWidget;
    private final IconHollowButton playButton;
    private final IAnimationValue animValue;

    public SMDAnimationWidget(int x, int y, int w, int h, IAnimationValue value, IAnimationValue onPlay) {
        super(x, y, w, h, new StringTextComponent("smd_textfield"));
        int width = w - 20;

        this.animValue = value;
        playButton = new IconHollowButton(x + w - 20, y, 20, 20, new StringTextComponent(""), (button) -> {
        }, new ResourceLocation(MODID, "textures/gui/text_field_mini.png"), new ResourceLocation(MODID, "textures/gui/play.png"));
        animNameWidget = new HollowTextFieldWidget(Minecraft.getInstance().font, x, y, width / 3, h, new StringTextComponent(""), GUIHelper.TEXT_FIELD);
        animWidget = new ResourceFieldWidget(Minecraft.getInstance().font, x + width / 3, y, (int) (0.667F * width), h, GUIHelper.TEXT_FIELD);
    }

    public void save() {
        this.animValue.save(this.animNameWidget.getValue(), new ResourceLocation(this.animWidget.getValue()));
    }

    @Override
    public void render(MatrixStack stack, int mouseX, int mouseY, float partialTicks) {
        updatePosition();
        this.animNameWidget.render(stack, mouseX, mouseY, partialTicks);
        this.animWidget.render(stack, mouseX, mouseY, partialTicks);
        this.playButton.render(stack, mouseX, mouseY, partialTicks);
    }

    private void updatePosition() {
        int calcWidth = this.width - 20;

        this.animNameWidget.x = this.x;
        this.animNameWidget.y = this.y;

        this.animWidget.x = this.x + calcWidth / 3;
        this.animWidget.y = this.y;

        this.playButton.x = this.x + this.width - 20;
        this.playButton.y = this.y;
    }

    @Override
    public boolean mouseClicked(double p_231044_1_, double p_231044_3_, int p_231044_5_) {
        this.animNameWidget.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);
        this.animWidget.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);
        this.playButton.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);

        return super.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);
    }

    @Override
    public boolean mouseReleased(double p_231048_1_, double p_231048_3_, int p_231048_5_) {
        this.animNameWidget.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);
        this.animWidget.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);
        this.playButton.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);

        return super.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);
    }

    @Override
    public void mouseMoved(double p_212927_1_, double p_212927_3_) {
        this.animNameWidget.mouseMoved(p_212927_1_, p_212927_3_);
        this.animWidget.mouseMoved(p_212927_1_, p_212927_3_);
        this.playButton.mouseMoved(p_212927_1_, p_212927_3_);
        super.mouseMoved(p_212927_1_, p_212927_3_);
    }

    @Override
    public boolean keyPressed(int p_231046_1_, int p_231046_2_, int p_231046_3_) {
        this.animNameWidget.keyPressed(p_231046_1_, p_231046_2_, p_231046_3_);
        this.animWidget.keyPressed(p_231046_1_, p_231046_2_, p_231046_3_);
        this.playButton.keyPressed(p_231046_1_, p_231046_2_, p_231046_3_);
        return super.keyPressed(p_231046_1_, p_231046_2_, p_231046_3_);
    }

    @Override
    public boolean keyReleased(int p_223281_1_, int p_223281_2_, int p_223281_3_) {
        this.animNameWidget.keyReleased(p_223281_1_, p_223281_2_, p_223281_3_);
        this.animWidget.keyReleased(p_223281_1_, p_223281_2_, p_223281_3_);
        this.playButton.keyReleased(p_223281_1_, p_223281_2_, p_223281_3_);
        return super.keyReleased(p_223281_1_, p_223281_2_, p_223281_3_);
    }

    @Override
    public boolean mouseScrolled(double p_231043_1_, double p_231043_3_, double delta) {
        this.animNameWidget.mouseScrolled(p_231043_1_, p_231043_3_, delta);
        this.animWidget.mouseScrolled(p_231043_1_, p_231043_3_, delta);
        this.playButton.mouseScrolled(p_231043_1_, p_231043_3_, delta);
        return super.mouseScrolled(p_231043_1_, p_231043_3_, delta);
    }

    @Override
    public boolean charTyped(char p_231042_1_, int p_231042_2_) {
        this.animNameWidget.charTyped(p_231042_1_, p_231042_2_);
        this.animWidget.charTyped(p_231042_1_, p_231042_2_);
        this.playButton.charTyped(p_231042_1_, p_231042_2_);
        return super.charTyped(p_231042_1_, p_231042_2_);
    }



    public interface IAnimationValue {
        void save(String animName, ResourceLocation animPath);
    }
}
