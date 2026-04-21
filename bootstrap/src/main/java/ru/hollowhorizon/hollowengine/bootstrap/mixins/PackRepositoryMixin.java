package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @ModifyVariable(at = @At("HEAD"), method = "<init>*", argsOnly = true)
    private static RepositorySource[] hollowengine$onInit(RepositorySource[] providers) {
        return BootstrapRuntimeManager.bridge().augmentPackRepositorySources(providers);
    }
}
