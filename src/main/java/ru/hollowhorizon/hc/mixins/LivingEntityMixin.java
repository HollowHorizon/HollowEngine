package ru.hollowhorizon.hc.mixins;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hc.common.utils.JavaHacks;
import ru.hollowhorizon.hc.common.events.EventBus;
import ru.hollowhorizon.hc.common.events.entity.LivingEntityDeathEvent;
import ru.hollowhorizon.hc.common.events.tick.TickEvent;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(CallbackInfo ci) {
        EventBus.post(new TickEvent.Entity(JavaHacks.forceCast(this)));
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    public void die(DamageSource damageSource, CallbackInfo ci) {
        var e = new LivingEntityDeathEvent(JavaHacks.forceCast(this), damageSource);
        EventBus.post(e);
        if (e.isCanceled()) ci.cancel();
    }
}
