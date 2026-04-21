package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void hollowengine$tick(CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onLivingEntityTick((LivingEntity) (Object) this);
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void hollowengine$die(DamageSource damageSource, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onLivingEntityDeath((LivingEntity) (Object) this, damageSource)) {
            ci.cancel();
        }
    }
}
