package ru.hollowhorizon.hollowengine.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import ru.hollowhorizon.hc.client.screens.HollowScreen;
import ru.hollowhorizon.hollowengine.client.render.GUIHelper;

import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;

public class NPCModelChoicerScreen extends HollowScreen {
    public static final ResourceLocation HOLLOW_CORE = new ResourceLocation(MODID, "textures/gui/model_loaders/hollow_core.png");
    public static final ResourceLocation TIME_CORE = new ResourceLocation(MODID, "textures/gui/model_loaders/time_core.png");
    public static final ResourceLocation VANILLA = new ResourceLocation(MODID, "textures/gui/model_loaders/vanilla.png");
    private final NPCCreationScreen lastScreen;
    private int firstCounter = 0;
    private int secondCounter = 0;
    private int thirdCounter = 0;

    protected NPCModelChoicerScreen(NPCCreationScreen lastScreen) {
        super(new TextComponent("NPC_MODEL_CHOICER_SCREEN"));
        this.lastScreen = lastScreen;
    }

    @Override
    public void render(PoseStack stack, int mouseX, int p_230430_3_, float p_230430_4_) {
        renderBackground(stack);
        super.render(stack, mouseX, p_230430_3_, p_230430_4_);
        for (int i = 0; i < 3; i++) {
            drawCard(stack, i, mouseX);
        }
    }

    public void drawCard(PoseStack stack, int modelType, double mouseX) {

        stack.pushPose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        var fr = Minecraft.getInstance().font;
        Component text;
        switch (modelType) {
            case 0:
                GUIHelper.drawIcon(stack, HOLLOW_CORE, 0, 0, this.width / 3, this.height, firstCounter / 70F + 0.7F);
                text = new TextComponent("Hollow Core");
                GUIHelper.drawCentredSizedString(stack, fr, text, this.width / 6, this.height / 2, 0xFFFFFF, firstCounter / 40F + 0.7F);
                GUIHelper.drawSizedStringWithWidth(stack, fr, new TextComponent("Добавляет модели со скелетными анимациями. Поддерживаемые форматы: .smd"),
                        this.width / 6, (int) (this.height - this.height / 2.5F), 100,
                        0xFFFFFF, firstCounter / 40F + 0.7F);
                if (mouseX < this.width / 3F) {
                    if (firstCounter < 20) firstCounter++;
                } else if (firstCounter > 0) firstCounter--;
                break;
            case 1:
                GUIHelper.drawIcon(stack, TIME_CORE, this.width / 3, 0, this.width / 3, this.height, secondCounter / 70F + 0.7F);
                text = new TextComponent("Time Core");
                GUIHelper.drawCentredSizedString(stack, fr, text, this.width / 2, this.height / 2, 0xFFFFFF, secondCounter / 40F + 0.7F);
                GUIHelper.drawSizedStringWithWidth(stack, fr, new TextComponent("Добавляет модели из BlockBench, в том числе и анимированные. Поддерживаемые форматы: .json"),
                        this.width / 2, (int) (this.height - this.height / 2.5F), 100,
                        0xFFFFFF, secondCounter / 40F + 0.7F);
                if (mouseX > this.width / 3F && mouseX < 0.6667F * this.width) {
                    if (secondCounter < 20) secondCounter++;
                } else if (secondCounter > 0) secondCounter--;
                break;
            case 2:
                GUIHelper.drawIcon(stack, VANILLA, (int) (0.6667F * this.width), 0, this.width / 3, this.height, thirdCounter / 70F + 0.7F);
                text = new TextComponent("Vanilla");
                GUIHelper.drawCentredSizedString(stack, fr, text, this.width - this.width / 6, this.height / 2, 0xFFFFFF, thirdCounter / 40F + 0.7F);
                GUIHelper.drawSizedStringWithWidth(stack, fr, new TextComponent("Добавляет модели, которые уже есть у других мобов в игре, пока только ванильных."),
                        this.width - this.width / 6, (int) (this.height - this.height / 2.5F), 100,
                        0xFFFFFF, thirdCounter / 40F + 0.7F);
                if (mouseX > 0.6667F * this.width) {
                    if (thirdCounter < 20) thirdCounter++;
                } else if (thirdCounter > 0) thirdCounter--;
        }

        stack.popPose();

    }

    @Override
    public boolean mouseClicked(double mouseX, double p_231044_3_, int button) {
        if(mouseX < this.width / 3F) {
            Minecraft.getInstance().setScreen(new SMDEditModelScreen(this, this.lastScreen));
        }
        return super.mouseClicked(mouseX, p_231044_3_, button);
    }
}
