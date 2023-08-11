package ru.hollowhorizon.hollowengine.client.screen.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget;
import ru.hollowhorizon.hollowengine.client.screen.NPCCreationScreen;
import ru.hollowhorizon.hollowengine.client.screen.NPCModelChoicerScreen;
import ru.hollowhorizon.hollowengine.client.render.GUIHelper;
import ru.hollowhorizon.hollowengine.client.screen.widget.button.SizedButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ModelEditWidget extends HollowWidget {
    private final List<AbstractWidget> buttons = new ArrayList<>();
    private final NPCCreationScreen npcScreen;
    private final HashMap<String, ResourceLocation> animations = new HashMap<>();
    private ResourceLocation modelLocation;
    private ResourceLocation textureLocation;

    public ModelEditWidget(int x, int y, int w, int h, NPCModelChoicerScreen screen, NPCCreationScreen npcScreen) {
        super(x, y, w, h, Component.literal("Создание Модели NPC"));

        this.npcScreen = npcScreen;

        this.addButton(new LabelWidget(this.x + this.width / 2, this.y + 10, Minecraft.getInstance().font, LabelWidget.AnchorX.CENTER, LabelWidget.AnchorY.CENTER, getMessage()));

        this.addButton(new ResourceFieldWidget(Minecraft.getInstance().font, this.x + this.width / 2 - this.width / 3, this.y + 40, (int) (this.width / 1.5F), 20, GUIHelper.TEXT_FIELD, (string) -> modelLocation = new ResourceLocation(string)));

        this.addButton(new ResourceFieldWidget(Minecraft.getInstance().font, this.x + this.width / 2 - this.width / 3, this.y + 70, (int) (this.width / 1.5F), 20, GUIHelper.TEXT_FIELD, (texture) -> modelLocation = new ResourceLocation(texture)));

        ListWidget list = new ListWidget(0, this.y + 95, this.width, (int) (this.height / 2.3F), new ArrayList<>(),
                () -> new SMDAnimationWidget(0, 0, (int) (this.width / 1.1F), 20, animations::put, (animName, animPath) -> {
                    this.npcScreen.updateAnimation(animName, animPath);
                    this.npcScreen.setCurrentAnimation(animName);
                }), Component.literal("text"));
        this.addButton(list);

        this.addButton(new SizedButton(this.x + this.width / 2 - this.width / 3, this.y + this.height - 30, this.width / 3, 20,
                Component.literal("Сохранить"), button -> {
            this.animations.clear();
            list.saveValues();
            this.save();
        }, GUIHelper.TEXT_FIELD, GUIHelper.TEXT_FIELD_LIGHT));
        this.addButton(new SizedButton(this.x + this.width / 2, this.y + this.height - 30, this.width / 3, 20,
                Component.literal("Отмена"), button -> Minecraft.getInstance().setScreen(screen), GUIHelper.TEXT_FIELD, GUIHelper.TEXT_FIELD_LIGHT));
    }

    private void save() {
        this.npcScreen.setModelLocation(modelLocation);
        this.npcScreen.setTextureLocation(textureLocation);
        this.npcScreen.setAnimations(animations);
    }

    @Override
    public void playDownSound(SoundManager p_230988_1_) {
    }

    public void addButton(AbstractWidget button) {
        this.buttons.add(button);
    }

    @Override
    public void renderButton(PoseStack p_230431_1_, int p_230431_2_, int p_230431_3_, float p_230431_4_) {
    }

    @Override
    public void render(PoseStack p_230430_1_, int p_230430_2_, int p_230430_3_, float p_230430_4_) {
        super.render(p_230430_1_, p_230430_2_, p_230430_3_, p_230430_4_);

        this.buttons.forEach(widget -> widget.render(p_230430_1_, p_230430_2_, p_230430_3_, p_230430_4_));
    }

    @Override
    public boolean mouseClicked(double p_231044_1_, double p_231044_3_, int p_231044_5_) {
        for (var widget : this.buttons) {
            widget.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);
        }
        return false;

    }

    @Override
    public boolean mouseReleased(double p_231048_1_, double p_231048_3_, int p_231048_5_) {
        for (var widget : this.buttons) {
            widget.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);
        }
        return super.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);
    }

    @Override
    public void mouseMoved(double p_212927_1_, double p_212927_3_) {
        for (var widget : this.buttons) {
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
        for (var widget : this.buttons) {
            if (widget.keyPressed(p_231046_1_, p_231046_2_, p_231046_3_)) return true;
        }
        return super.keyPressed(p_231046_1_, p_231046_2_, p_231046_3_);
    }

    @Override
    public boolean keyReleased(int p_223281_1_, int p_223281_2_, int p_223281_3_) {
        for (var widget : this.buttons) {
            if (widget.keyReleased(p_223281_1_, p_223281_2_, p_223281_3_)) return true;
        }
        return super.keyReleased(p_223281_1_, p_223281_2_, p_223281_3_);
    }

    @Override
    public boolean mouseDragged(double p_231045_1_, double p_231045_3_, int p_231045_5_, double p_231045_6_, double p_231045_8_) {
        for (var widget : this.buttons) {
            if (widget.mouseDragged(p_231045_1_, p_231045_3_, p_231045_5_, p_231045_6_, p_231045_8_)) return true;
        }
        return super.mouseDragged(p_231045_1_, p_231045_3_, p_231045_5_, p_231045_6_, p_231045_8_);
    }

    @Override
    public boolean charTyped(char p_231042_1_, int p_231042_2_) {
        for (var widget : this.buttons) {
            if (widget.charTyped(p_231042_1_, p_231042_2_)) return true;
        }
        return super.charTyped(p_231042_1_, p_231042_2_);
    }
}
