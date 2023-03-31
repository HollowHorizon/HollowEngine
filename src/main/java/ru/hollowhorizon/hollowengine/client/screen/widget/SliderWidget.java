package ru.hollowhorizon.hollowengine.client.screen.widget;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SimpleSound;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import ru.hollowhorizon.hollowengine.client.sound.HSSounds;

import java.util.function.Consumer;

import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;

public class SliderWidget extends Widget {
    public static final ResourceLocation SLIDER_BASE = new ResourceLocation(MODID, "textures/gui/slider_base.png");
    private final Consumer<Boolean> consumer;
    private boolean value;
    private boolean processAnim;
    private int processCounter;

    public SliderWidget(int x, int y, int w, int h, Consumer<Boolean> consumer) {
        super(x, y, w, h, new StringTextComponent(""));
        this.consumer = consumer;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    @Override
    public void render(MatrixStack stack, int mouseX, int mouseY, float p_230430_4_) {
        this.isHovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
        TextureManager manager = Minecraft.getInstance().textureManager;

        manager.bind(SLIDER_BASE);
        blit(stack, this.x, this.y, 0, 0, this.width, this.height, this.width, this.height * 3);

        stack.pushPose();
        RenderSystem.enableAlphaTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.defaultAlphaFunc();
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, processCounter / 10F);

        manager.bind(SLIDER_BASE);
        blit(stack, this.x, this.y, 0, this.height * 2, this.width, this.height, this.width, this.height * 3);

        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1F);
        RenderSystem.disableAlphaTest();
        RenderSystem.disableBlend();
        stack.popPose();

        manager.bind(SLIDER_BASE);
        blit(stack, this.x + (int) (0.6667F * this.width * processCounter / 10F), this.y, 0, this.height, this.width, this.height, this.width, this.height * 3);

        if (this.processAnim) {
            if (this.value) {
                if (processCounter < 10) processCounter += 1;
                else this.processAnim = false;
            } else {
                if (processCounter > 0) processCounter -= 1;
                else this.processAnim = false;
            }
        }
    }

    @Override
    public boolean mouseClicked(double p_231044_1_, double p_231044_3_, int button) {
        if (this.isHovered && button == 0) {
            this.value = !this.value;
            this.consumer.accept(this.value);
            this.processAnim = true;
            Minecraft.getInstance().getSoundManager().play(SimpleSound.forUI(HSSounds.SLIDER_BUTTON, 1.0F));
            return true;
        }
        return false;
    }
}
