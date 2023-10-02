package ru.hollowhorizon.hollowengine.client.screen.widget;


import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget;
import ru.hollowhorizon.hollowengine.client.render.GUIHelper;

import java.util.ArrayList;
import java.util.function.Consumer;

import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;
import static ru.hollowhorizon.hollowengine.common.TextHelperKt.empty;

public class DropListWidget extends HollowWidget {
    public static final ResourceLocation LIST = new ResourceLocation(MODID, "textures/gui/list_icon.png");
    private final ArrayList<ListElement> elements;
    private final Consumer<ListElement> onClick;
    private final Component text;
    private ListElement currentElement;
    private int elementOffset = 0;
    private int elementLimit = 5;
    private boolean isLastHovered;
    private int waitCounter = 0;
    private int animCounter = 0;

    public DropListWidget(Component text, ArrayList<ListElement> elements, Consumer<ListElement> onClick, int x, int y, int w, int h) {
        super(x, y, w, h, empty());

        this.elements = elements;
        this.onClick = onClick;
        this.text = text;
        if (elements.size() < 5) elementLimit = elements.size();
    }

    @Override
    public void render(PoseStack stack, int mouseX, int mouseY, float p_230430_4_) {
        if (!isLastHovered) {
            boolean isHovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            if (isHovered) waitCounter++;
            this.isLastHovered = waitCounter > 10;
        } else {
            waitCounter = 0;
        }

        if (isLastHovered)
            this.isHovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height * (this.elementLimit + 1);

        if (this.currentElement != null) {
            GUIHelper.drawTextInBox(stack, this.text.copy().append(": ").append(this.currentElement.getTextComponent()), this.x, this.y, this.width);
        } else GUIHelper.drawTextInBox(stack, this.text, this.x, this.y, this.width);

        stack.pushPose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float color = this.isHovered ? 0.3F : 1.0F;
        GUIHelper.drawIcon(stack, LIST, this.x + this.width - this.height, this.y, this.height, this.height, 0.6F, color, color, color, 1.0F);

        stack.popPose();

        if (!isHovered) {
            this.isLastHovered = false;
            this.animCounter = 0;
            return;
        }

        float alpha = animCounter / 15F;

        stack.pushPose();

        for (int i = this.elementOffset; i < this.elementOffset + this.elementLimit; i++) {
            int yPos = this.y + this.height + (i - this.elementOffset) * this.height;
            ListElement element = this.elements.get(i);

            if (i - this.elementOffset == this.getButton(mouseY)) {
                GUIHelper.drawTextInBox(stack, GUIHelper.TEXT_FIELD_LIGHT, element.getTextComponent(), this.x, yPos, this.width, alpha);
            } else {
                GUIHelper.drawTextInBox(stack, GUIHelper.TEXT_FIELD, element.getTextComponent(), this.x, yPos, this.width, alpha);
            }

            GUIHelper.drawIcon(stack, element.getIcon(), this.x, yPos, 20, 20, 0.6F, alpha);
        }

        stack.popPose();

        if (animCounter < 15) animCounter++;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.elementLimit < 6) return false;

        if (delta > 0) {
            if (this.elementOffset < this.elementLimit - 5) {
                this.elementOffset++;
                return true;
            }
        } else {
            if (this.elementOffset > 0) {
                this.elementOffset--;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int b = getButton(mouseY);

        if (b != -1) {
            this.currentElement = this.elements.get(elementOffset + b);
            this.onClick.accept(this.currentElement);
            //Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(HSSounds.SLIDER_BUTTON, 1.0F));
            this.animCounter = 0;
            this.isLastHovered = false;
            this.isHovered = false;
            return true;
        }

        return false;
    }

    public int getButton(double mouseY) {
        double posY = mouseY - this.y - this.height;
        if (!isHovered || posY <= 0) return -1;
        return (int) (posY / this.height);
    }
}
