package ru.hollowhorizon.hollowengine.client.render.enitites;

import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import ru.hollowhorizon.hollowengine.HollowEngine;
import ru.hollowhorizon.hollowengine.client.model.ThomasModel;
import ru.hollowhorizon.hollowengine.common.entities.ThomasNPC;

public class ThomasRenderer extends MobRenderer<ThomasNPC, ThomasModel<ThomasNPC>> {
    public ThomasRenderer(EntityRendererManager entityRendererManager, IReloadableResourceManager manager) {
        super(entityRendererManager, new ThomasModel<>(), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(ThomasNPC pEntity) {
        return new ResourceLocation(HollowEngine.MODID, "textures/entities/thomas.png");
    }
}
