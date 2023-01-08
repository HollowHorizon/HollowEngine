package ru.hollowhorizon.hollowstory.client.gui.widget;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.StringTextComponent;
import ru.hollowhorizon.hollowstory.client.gui.NPCCreationScreen;
import ru.hollowhorizon.hollowstory.client.gui.NPCModelChoicerScreen;
import ru.hollowhorizon.hollowstory.client.render.GUIHelper;
import ru.hollowhorizon.hollowstory.client.gui.NPCCreationScreen;
import ru.hollowhorizon.hollowstory.client.gui.NPCModelChoicerScreen;
import ru.hollowhorizon.hollowstory.client.gui.widget.button.SizedButton;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ModelEditWidget extends Widget {
    private final List<Widget> buttons = new ArrayList<>();
    private final NPCCreationScreen npcScreen;
    private final HashMap<String, ResourceLocation> animations = new HashMap<>();
    private ResourceLocation modelLocation;
    private ResourceLocation textureLocation;

    public ModelEditWidget(int x, int y, int w, int h, NPCModelChoicerScreen screen, NPCCreationScreen npcScreen) {
        super(x, y, w, h, new StringTextComponent("Создание Модели NPC"));

        this.npcScreen = npcScreen;

        this.addButton(new LabelWidget(this.x + this.width / 2, this.y + 10, Minecraft.getInstance().font, LabelWidget.AnchorX.CENTER, LabelWidget.AnchorY.CENTER, getMessage()));

        this.addButton(new ResourceFieldWidget(Minecraft.getInstance().font, this.x + this.width / 2 - this.width / 3, this.y + 40, (int) (this.width / 1.5F), 20, GUIHelper.TEXT_FIELD, (string) -> modelLocation = new ResourceLocation(string)));

        this.addButton(new ResourceFieldWidget(Minecraft.getInstance().font, this.x + this.width / 2 - this.width / 3, this.y + 70, (int) (this.width / 1.5F), 20, GUIHelper.TEXT_FIELD, (texture) -> modelLocation = new ResourceLocation(texture)));

        ListWidget list = new ListWidget(0, this.y + 95, this.width, (int) (this.height / 2.3F), new ArrayList<>(),
                () -> new SMDAnimationWidget(0, 0, (int) (this.width / 1.1F), 20, animations::put, (animName, animPath) -> {
                    this.npcScreen.updateAnimation(animName, animPath);
                    this.npcScreen.setCurrentAnimation(animName);
                }), new StringTextComponent("text"));
        this.addButton(list);

        this.addButton(new SizedButton(this.x + this.width / 2 - this.width / 3, this.y + this.height - 30, this.width / 3, 20,
                new StringTextComponent("Сохранить"), button -> {
            this.animations.clear();
            list.saveValues();
            this.save();
        }, GUIHelper.TEXT_FIELD, GUIHelper.TEXT_FIELD_LIGHT));
        this.addButton(new SizedButton(this.x + this.width / 2, this.y + this.height - 30, this.width / 3, 20,
                new StringTextComponent("Отмена"), button -> Minecraft.getInstance().setScreen(screen), GUIHelper.TEXT_FIELD, GUIHelper.TEXT_FIELD_LIGHT));
    }

    private void save() {
        this.npcScreen.setModelLocation(modelLocation);
        this.npcScreen.setTextureLocation(textureLocation);
        this.npcScreen.setAnimations(animations);
    }

    @Override
    public void playDownSound(SoundHandler p_230988_1_) {
    }

    public void addButton(Widget button) {
        this.buttons.add(button);
    }

    @Override
    public void renderButton(MatrixStack p_230431_1_, int p_230431_2_, int p_230431_3_, float p_230431_4_) {
    }

    @Override
    public void render(MatrixStack p_230430_1_, int p_230430_2_, int p_230430_3_, float p_230430_4_) {
        super.render(p_230430_1_, p_230430_2_, p_230430_3_, p_230430_4_);

        this.buttons.forEach(widget -> widget.render(p_230430_1_, p_230430_2_, p_230430_3_, p_230430_4_));
    }

    @Override
    public boolean mouseClicked(double p_231044_1_, double p_231044_3_, int p_231044_5_) {
        for (Widget widget : this.buttons) {
            widget.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);
        }
        return false;

    }

    @Override
    public boolean mouseReleased(double p_231048_1_, double p_231048_3_, int p_231048_5_) {
        for (Widget widget : this.buttons) {
            widget.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);
        }
        return super.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);
    }

    @Override
    public void mouseMoved(double p_212927_1_, double p_212927_3_) {
        for (Widget widget : this.buttons) {
            widget.mouseMoved(p_212927_1_, p_212927_3_);
        }
        super.mouseMoved(p_212927_1_, p_212927_3_);
    }

    @Override
    public boolean mouseScrolled(double p_231043_1_, double p_231043_3_, double p_231043_5_) {
        this.buttons.forEach(button -> button.mouseScrolled(p_231043_1_, p_231043_3_, p_231043_5_));
        return super.mouseScrolled(p_231043_1_, p_231043_3_, p_231043_5_);
    }

    @Override
    public boolean keyPressed(int p_231046_1_, int p_231046_2_, int p_231046_3_) {
        for (Widget widget : this.buttons) {
            if (widget.keyPressed(p_231046_1_, p_231046_2_, p_231046_3_)) return true;
        }
        return super.keyPressed(p_231046_1_, p_231046_2_, p_231046_3_);
    }

    @Override
    public boolean keyReleased(int p_223281_1_, int p_223281_2_, int p_223281_3_) {
        for (Widget widget : this.buttons) {
            if (widget.keyReleased(p_223281_1_, p_223281_2_, p_223281_3_)) return true;
        }
        return super.keyReleased(p_223281_1_, p_223281_2_, p_223281_3_);
    }

    @Override
    public boolean mouseDragged(double p_231045_1_, double p_231045_3_, int p_231045_5_, double p_231045_6_, double p_231045_8_) {
        for (Widget widget : this.buttons) {
            if (widget.mouseDragged(p_231045_1_, p_231045_3_, p_231045_5_, p_231045_6_, p_231045_8_)) return true;
        }
        return super.mouseDragged(p_231045_1_, p_231045_3_, p_231045_5_, p_231045_6_, p_231045_8_);
    }

    @Override
    public boolean charTyped(char p_231042_1_, int p_231042_2_) {
        for (Widget widget : this.buttons) {
            if (widget.charTyped(p_231042_1_, p_231042_2_)) return true;
        }
        return super.charTyped(p_231042_1_, p_231042_2_);
    }
}
