package ru.hollowhorizon.hollowengine.mixins.client;

import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import ru.hollowhorizon.hollowengine.client.particles.ParticleSystem;
import ru.hollowhorizon.hollowengine.api.ParticlesProvider;

@Mixin(ClientLevel.class)
public class ClientLevelMixin implements ParticlesProvider {

    @Unique
    private ParticleSystem hollowcore$system = ParticleSystem.Companion.create((ClientLevel) (Object) this);

    @Override
    public @NotNull ParticleSystem getSystem() {
        return hollowcore$system;
    }
}
