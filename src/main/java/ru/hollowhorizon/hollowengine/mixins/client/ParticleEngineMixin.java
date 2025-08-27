package ru.hollowhorizon.hollowengine.mixins.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterParticlesEvent;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    public void init(ClientLevel level, TextureManager textureManager, CallbackInfo ci) {
        EventBus.post(new RegisterParticlesEvent((ParticleEngine) (Object) this));
    }
}
