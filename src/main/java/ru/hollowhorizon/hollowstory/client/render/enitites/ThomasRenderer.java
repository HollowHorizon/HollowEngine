package ru.hollowhorizon.hollowstory.client.render.enitites;

import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import ru.hollowhorizon.hollowstory.client.model.ThomasModel;
import ru.hollowhorizon.hollowstory.common.entities.ThomasNPC;

public class ThomasRenderer extends MobRenderer<ThomasNPC, ThomasModel<ThomasNPC>> {
    public ThomasRenderer(EntityRendererManager entityRendererManager, IReloadableResourceManager manager) {
        super(entityRendererManager, new ThomasModel<>(), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(ThomasNPC pEntity) {
        return new ResourceLocation("hollowstory:textures/entities/thomas.png");
    }
}
