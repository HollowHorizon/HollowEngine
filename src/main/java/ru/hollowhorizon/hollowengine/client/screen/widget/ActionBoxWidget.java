package ru.hollowhorizon.hollowengine.client.screen.widget;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.util.text.StringTextComponent;
import ru.hollowhorizon.hc.client.screens.widget.HollowWidget;

import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;

public class ActionBoxWidget extends HollowWidget {
    public ActionBoxWidget(int x, int y, int width, int height) {
        super(x, y, width, height, new StringTextComponent(""));
    }

    @Override
    public void renderButton(MatrixStack stack, int mouseX, int mouseY, float ticks) {
        bind(MODID, "gui/widgets/element_panel.png");
        blit(stack, this.x, this.y, 0, 0, this.width, this.height, this.width, this.height);
        super.renderButton(stack, mouseX, mouseY, ticks);
    }
}
