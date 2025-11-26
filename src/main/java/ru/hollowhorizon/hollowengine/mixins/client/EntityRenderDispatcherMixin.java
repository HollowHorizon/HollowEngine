package ru.hollowhorizon.hollowengine.mixins.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.client.render.AddEntityRendererLayers;
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderEntityEvent;

import java.util.Map;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @Shadow
    public Map<EntityType<?>, EntityRenderer<?>> renderers;

    @Shadow
    private Map<String, EntityRenderer<? extends Player>> playerRenderers;

    @Inject(
            method = "onResourceManagerReload",
            at = @At("TAIL")
    )
    public void onResourceManagerReload(ResourceManager resourceManager, CallbackInfo ci, @Local EntityRendererProvider.Context context) {
        EventBus.post(new AddEntityRendererLayers(this.renderers, this.playerRenderers, context));
    }

    @WrapOperation(
            method = {"render"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V")
    )
    public <T extends Entity> void onRenderEntity(EntityRenderer<T> instance, T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, Operation<Void> original) {
        var event = new RenderEntityEvent.Pre(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        EventBus.post(event);
        if (event.isCanceled()) return;
        original.call(instance, entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        EventBus.post(new RenderEntityEvent.Post(entity, entityYaw, partialTick, poseStack, buffer, packedLight));
    }
}
