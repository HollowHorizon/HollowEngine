package ru.hollowhorizon.hollowengine.mixins.client;

import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.api.ICapabilityDispatcherKt;
import ru.hollowhorizon.hollowengine.client.particles.ParticleSystem;
import ru.hollowhorizon.hollowengine.api.ParticlesProvider;
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks;

@Mixin(ClientLevel.class)
public class ClientLevelMixin implements ParticlesProvider {

    @Unique
    private ParticleSystem hollowcore$system = ParticleSystem.Companion.create((ClientLevel) (Object) this);

    @Inject(method = "tick", at = @At("TAIL"))
    public void onTick(CallbackInfo ci) {
        ICapabilityDispatcherKt.syncIfNeeded(JavaHacks.forceCast(this));
    }

    @Override
    public @NotNull ParticleSystem getSystem() {
        return hollowcore$system;
    }
}
