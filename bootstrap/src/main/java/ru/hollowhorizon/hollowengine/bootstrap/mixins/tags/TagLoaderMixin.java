package ru.hollowhorizon.hollowengine.bootstrap.mixins.tags;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagLoader;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mixin(TagLoader.class)
public class TagLoaderMixin {
    @Shadow
    @Final
    private String directory;

    @Inject(method = "build(Ljava/util/Map;)Ljava/util/Map;", at = @At("HEAD"))
    private void hollowengine$load(Map<ResourceLocation, List<TagLoader.EntryWithSource>> value, CallbackInfoReturnable<Map<ResourceLocation, Collection<?>>> cir) {
        BuiltInRegistries.REGISTRY.stream()
                .filter(t -> Registries.tagsDirPath(t.key()).equals(directory))
                .findFirst()
                .ifPresent(reg -> BootstrapRuntimeManager.bridge().onRegisterTags(reg, value));
    }
}
