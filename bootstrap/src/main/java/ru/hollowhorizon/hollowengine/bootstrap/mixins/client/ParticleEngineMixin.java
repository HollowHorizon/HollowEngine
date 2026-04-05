package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    public void hollowengine$init(ClientLevel level, TextureManager textureManager, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onRegisterParticles((ParticleEngine) (Object) this);
    }
}
