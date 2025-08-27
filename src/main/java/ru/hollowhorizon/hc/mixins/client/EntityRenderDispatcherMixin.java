package ru.hollowhorizon.hc.mixins.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hc.common.events.EventBus;
import ru.hollowhorizon.hc.common.events.client.render.AddEntityRendererLayers;
import java.util.Map;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Shadow private Map<EntityType<?>, EntityRenderer<?>> renderers;

    @Shadow private Map<String, EntityRenderer<? extends Player>> playerRenderers;

    @Inject(
            method = "onResourceManagerReload",
            at = @At("TAIL")
    )
    public void onResourceManagerReload(ResourceManager resourceManager, CallbackInfo ci, @Local EntityRendererProvider.Context context) {
        EventBus.post(new AddEntityRendererLayers(this.renderers, this.playerRenderers, context));
    }
}
