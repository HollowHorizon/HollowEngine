package ru.hollowhorizon.hollowstory.common.npcs;

import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.vertex.IVertexBuilder;
import net.minecraft.client.renderer.IRenderTypeBuffer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.ResourceLocation;

public interface INPCModel {
    void render(MatrixStack stack, IRenderTypeBuffer builder, int packedLight, int packedOverlay, float red, float green, float blue, float alpha);

    void setTexture(ResourceLocation location);

    ResourceLocation getTexture();

    RenderType renderType(ResourceLocation location);

    void animateModel();

    void setAnimation(String animationName, int speed);

    NPCModelType type();

    enum NPCModelType {
        HOLLOW_CORE,
        TIME_CORE,
        VANILLA
    }
}
