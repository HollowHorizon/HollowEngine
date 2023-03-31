package ru.hollowhorizon.hollowengine.client.render.enitites;

import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.ResourceLocation;
import ru.hollowhorizon.hollowengine.HollowEngine;
import ru.hollowhorizon.hollowengine.client.model.SindyModel;
import ru.hollowhorizon.hollowengine.common.entities.SindyNPC;

public class SindyRenderer extends MobRenderer<SindyNPC, SindyModel<SindyNPC>> {
    public SindyRenderer(EntityRendererManager entityRendererManager) {
        super(entityRendererManager, new SindyModel<>(), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(SindyNPC pEntity) {
        return new ResourceLocation(HollowEngine.MODID, "textures/entities/sindy.png");
    }
}
