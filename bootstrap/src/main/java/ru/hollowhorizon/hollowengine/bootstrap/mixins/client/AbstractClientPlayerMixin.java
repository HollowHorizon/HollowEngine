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

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void onGetSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        ResourceLocation texture = BootstrapRuntimeManager.bridge()
                .getCustomPlayerSkinTexture((AbstractClientPlayer) (Object) this);
        if (texture == null) return;

        ResourceLocation cape = BootstrapRuntimeManager.bridge()
                .getCustomPlayerSkinCape((AbstractClientPlayer) (Object) this);
        boolean slim = BootstrapRuntimeManager.bridge()
                .isCustomPlayerSkinSlim((AbstractClientPlayer) (Object) this);

        PlayerSkin.Model model = slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE;
        cir.setReturnValue(new PlayerSkin(texture, null, cape, null, model, false));
    }
}
