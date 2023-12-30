package ru.hollowhorizon.hollowengine.client.screen;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import ru.hollowhorizon.hc.client.screens.HollowScreen;
import ru.hollowhorizon.hollowengine.client.render.GUIHelper;
import ru.hollowhorizon.hollowengine.client.screen.widget.DropListWidget;
import ru.hollowhorizon.hollowengine.client.screen.widget.ListElement;
import ru.hollowhorizon.hollowengine.client.screen.widget.SliderWidget;
import ru.hollowhorizon.hollowengine.client.screen.widget.button.SizedButton;

import java.util.ArrayList;
import java.util.HashMap;

import static ru.hollowhorizon.hc.client.utils.ForgeKotlinKt.toTTC;
import static ru.hollowhorizon.hollowengine.HollowEngine.MODID;

@OnlyIn(Dist.CLIENT)
public class NPCCreationScreen extends HollowScreen {
    public static final int startY = 20;
    private final HashMap<String, ResourceLocation> animations = new HashMap<>();
    private ResourceLocation modelLocation;
    private ResourceLocation textureLocation;
    private boolean isChanged = false;
    private DropListWidget list;
    private String currentAnimation;
    private boolean shouldDespawn;
    private Boolean isUndead;

    protected NPCCreationScreen() {}

    @Override
    protected void init() {
        int halfWidth = this.width / 2;

        ArrayList<ListElement> elements = new ArrayList<>();
        elements.add(new ListElement(new ResourceLocation(MODID, "public"), toTTC("hollowengine.npc_creation.public"), new ResourceLocation(MODID, "textures/gui/planet.png")));
        elements.add(new ListElement(new ResourceLocation(MODID, "private"), toTTC("hollowengine.npc_creation.private"), new ResourceLocation(MODID, "textures/gui/private.png")));
        elements.add(new ListElement(new ResourceLocation(MODID, "companion"), toTTC("hollowengine.npc_creation.companion"), new ResourceLocation(MODID, "textures/gui/heart.png")));

        this.addRenderableWidget(new SliderWidget(halfWidth + 100, startY + 60, 50, 20, this::setShouldDespawn));
        this.addRenderableWidget(new SliderWidget(halfWidth + 100, startY + 80, 50, 20, this::setUndead));
        this.addRenderableWidget(new SizedButton(halfWidth - 150, startY + 40, 300, 20, toTTC("hollowengine.npc_creation.model"), button -> Minecraft.getInstance().setScreen(new NPCModelChoicerScreen(this)), GUIHelper.TEXT_FIELD, GUIHelper.TEXT_FIELD_LIGHT));

        this.list = new DropListWidget(toTTC("hollowengine.npc_creation.choice_type"), elements, (element -> System.out.println(element.getTextComponent().getString())), halfWidth - 150, startY + 20, 300, 20);
    }

    @Override
    public void render(PoseStack stack, int p_230430_2_, int p_230430_3_, float p_230430_4_) {
        this.renderBackground(stack);
        int halfWidth = this.width / 2;

        GUIHelper.drawTextInBox(stack, toTTC("hollowengine.npc_creation"), halfWidth - 150, startY, 300);
        GUIHelper.drawTextInBox(stack, toTTC("hollowengine.npc_creation.undead"), halfWidth - 150, startY + 60, 250);
        GUIHelper.drawTextInBox(stack, toTTC("hollowengine.npc_creation.despawn"), halfWidth - 150, startY + 80, 250);

        super.render(stack, p_230430_2_, p_230430_3_, p_230430_4_);

        this.list.render(stack, p_230430_2_, p_230430_3_, p_230430_4_);
    }

    @Override
    public boolean mouseClicked(double p_231044_1_, double p_231044_3_, int p_231044_5_) {
        if (this.list.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_)) return true;
        return super.mouseClicked(p_231044_1_, p_231044_3_, p_231044_5_);
    }

    @Override
    public boolean mouseReleased(double p_231048_1_, double p_231048_3_, int p_231048_5_) {
        if (this.list.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_)) return true;
        return super.mouseReleased(p_231048_1_, p_231048_3_, p_231048_5_);
    }

    public ResourceLocation getModelLocation() {
        return modelLocation;
    }

    public void setModelLocation(ResourceLocation modelLocation) {
        isChanged = true;
        this.modelLocation = modelLocation;
    }

    public ResourceLocation getTextureLocation() {
        return textureLocation;
    }

    public void setTextureLocation(ResourceLocation textureLocation) {
        isChanged = true;
        this.textureLocation = textureLocation;
    }

    public String getCurrentAnimation() {
        return currentAnimation;
    }

    public void setCurrentAnimation(String currentAnimation) {
        isChanged = true;
        this.currentAnimation = currentAnimation;
    }

    public void updateAnimation(String str, ResourceLocation anim) {
        isChanged = true;
        this.animations.put(str, anim);
    }

    public void setShouldDespawn(boolean shouldDespawn) {
        isChanged = true;
        this.shouldDespawn = shouldDespawn;
    }

    public boolean isChanged() {
        return isChanged;
    }

    public void setChanged(boolean changed) {
        isChanged = changed;
    }

    public boolean shouldDespawn() {
        return this.shouldDespawn;
    }

    public HashMap<String, ResourceLocation> getAnimations() {
        return animations;
    }

    public void setAnimations(HashMap<String, ResourceLocation> animations) {
        isChanged = true;
        this.animations.clear();
        this.animations.putAll(animations);
    }

    public boolean isUndead() {
        return this.isUndead;
    }

    private void setUndead(Boolean aBoolean) {
        this.isChanged = true;
        this.isUndead = aBoolean;
    }
}
