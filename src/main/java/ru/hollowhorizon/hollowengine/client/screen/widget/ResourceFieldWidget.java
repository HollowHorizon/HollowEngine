package ru.hollowhorizon.hollowengine.client.screen.widget;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import ru.hollowhorizon.hollowengine.client.screen.widget.button.IconHollowButton;

import java.util.function.Consumer;

import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;
import static ru.hollowhorizon.hollowengine.common.TextHelperKt.literal;

public class ResourceFieldWidget extends HollowTextFieldWidget {

    private final IconHollowButton button;

    public ResourceFieldWidget(Font fr, int x, int y, int w, int h, ResourceLocation texture, Consumer<String> stringConsumer) {
        this(fr, x, y, w, h, texture);
        this.setResponder(stringConsumer);
    }

    public ResourceFieldWidget(Font fr, int x, int y, int w, int h, ResourceLocation texture) {
        super(fr, x, y, w, h, new TextComponent(""), texture);
        this.button = new IconHollowButton(this.x + this.width - this.height, this.y, this.height, this.height, new TextComponent(""), () -> {
//            HollowJavaUtils.chooseFile(
//                    fileChooser -> fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Model File", "*.smd")),
//                    file -> {
//                        SEND_MODEL_FILE.sendToServer(file);
//                        this.setValue("hollow-story/models/" + file.getName());
//                    }
//
//            );
        }, new ResourceLocation(MODID, "textures/gui/text_field_mini.png"), new ResourceLocation(MODID, "textures/gui/folder.png"));
    }

    @Override
    public void render(PoseStack stack, int p_230430_2_, int p_230430_3_, float p_230430_4_) {
        super.render(stack, p_230430_2_, p_230430_3_, p_230430_4_);
        this.button.render(stack, p_230430_2_, p_230430_3_, p_230430_4_);
    }

    @Override
    public boolean mouseClicked(double p_231044_1_, double p_231044_3_, int p_231044_5_) {
        if (this.button.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_)) return true;
        return super.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);
    }

    @Override
    public boolean mouseReleased(double p_231048_1_, double p_231048_3_, int p_231048_5_) {
        if (this.button.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_)) return true;
        return super.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);
    }
}