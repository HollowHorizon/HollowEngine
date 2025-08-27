package ru.hollowhorizon.hc.mixins.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability;
import ru.hollowhorizon.hc.client.render.entity.HollowEntityRenderer;
import ru.hollowhorizon.hc.common.events.EventBus;
import ru.hollowhorizon.hc.common.events.client.render.RenderPlayerEvent;
import ru.hollowhorizon.hc.common.utils.ForgeKotlinKt;

@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"), cancellable = true)
    private void onRenderPlayer(AbstractClientPlayer entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        RenderPlayerEvent event = new RenderPlayerEvent(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        EventBus.post(event);
        if (event.isCanceled()) ci.cancel();
    }

    @Inject(method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;", at = @At("HEAD"), cancellable = true)
    private void onRenderOffset(AbstractClientPlayer entity, float partialTicks, CallbackInfoReturnable<Vec3> cir) {
        if (!ForgeKotlinKt.get(entity, AnimatedEntityCapability.class).getModel().equals(HollowEntityRenderer.NO_MODEL)) {
            cir.setReturnValue(super.getRenderOffset(entity, partialTicks));
        }
    }
}
