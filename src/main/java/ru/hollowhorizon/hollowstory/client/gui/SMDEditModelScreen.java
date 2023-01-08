package ru.hollowhorizon.hollowstory.client.gui;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.text.StringTextComponent;
import ru.hollowhorizon.hc.client.utils.ScissorUtil;
import ru.hollowhorizon.hollowstory.client.gui.NPCCreationScreen;
import ru.hollowhorizon.hollowstory.client.gui.NPCModelChoicerScreen;
import ru.hollowhorizon.hollowstory.client.gui.widget.ModelEditWidget;
import ru.hollowhorizon.hollowstory.client.gui.widget.ModelPreviewWidget;
import ru.hollowhorizon.hollowstory.common.entities.NPCEntity;
import ru.hollowhorizon.hollowstory.common.npcs.NPCSettings;

public class SMDEditModelScreen extends Screen {
    private final NPCModelChoicerScreen lastScreen;
    private final NPCCreationScreen npcScreen;
    private ModelPreviewWidget preview;

    protected SMDEditModelScreen(NPCModelChoicerScreen lastScreen, NPCCreationScreen screen) {
        super(new StringTextComponent("EDIT_MODEL_SCREEN"));
        this.lastScreen = lastScreen;
        this.npcScreen = screen;

        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(true);
    }

    @Override
    protected void init() {

        this.addButton(new ModelEditWidget(0, 0, this.width / 2, this.height, this.lastScreen, this.npcScreen));
        //this.preview = new ModelPreviewWidget(this.npcScreen, this.width / 2, 0, this.width / 2, this.height, new NPCEntity(new NPCSettings(), Minecraft.getInstance().level), this::renderWidgetTooltip);
        this.addWidget(this.preview);
    }

    public void renderWidgetTooltip(Widget widget, MatrixStack stack, int mouseX, int mouseY) {
        this.renderTooltip(stack, widget.getMessage(), mouseX, mouseY);

    }

    @Override
    public void render(MatrixStack stack, int mouseX, int mouseY, float partialTicks) {
        super.renderBackground(stack);

        preview.render(stack, mouseX, mouseY, partialTicks);
        final float fullscreenness = preview.getFullscreenness();
        if (fullscreenness < 1.0F) {
            final boolean shouldScissor = fullscreenness > 0.0F;
            if (shouldScissor) {
                ScissorUtil.start(0, 0, width - preview.getWidth(), height);
            }

            super.render(stack, mouseX, mouseY, partialTicks);

            if (shouldScissor) {
                ScissorUtil.stop();
            }
        }
    }

    @Override
    public boolean mouseClicked(double p_231044_1_, double p_231044_3_, int p_231044_5_) {
        if (this.preview.getFullscreenness() > 0.9F) {
            return this.preview.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);
        }
        return super.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);
    }

    @Override
    public boolean mouseReleased(double p_231048_1_, double p_231048_3_, int p_231048_5_) {
        return super.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);
    }

    @Override
    public boolean keyPressed(int p_231046_1_, int p_231046_2_, int p_231046_3_) {
        return super.keyPressed(p_231046_1_, p_231046_2_, p_231046_3_);
    }

    @Override
    public boolean keyReleased(int p_223281_1_, int p_223281_2_, int p_223281_3_) {
        this.children.forEach(iGuiEventListener -> iGuiEventListener.keyReleased(p_223281_1_, p_223281_2_, p_223281_3_));
        return super.keyReleased(p_223281_1_, p_223281_2_, p_223281_3_);
    }

    @Override
    public void removed() {
        super.removed();
        Minecraft.getInstance().keyboardHandler.setSendRepeatsToGui(false);
    }
}
