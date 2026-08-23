package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin extends LivingEntity {
    protected AbstractClientPlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void onGetSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin custom = BootstrapRuntimeManager.bridge()
                .customizePlayerSkin((AbstractClientPlayer) (Object) this, cir.getReturnValue());
        if (custom != null) cir.setReturnValue(custom);
    }
}
