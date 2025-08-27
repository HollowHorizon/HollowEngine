package ru.hollowhorizon.hollowengine.mixins.tags;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagLoader;
import net.minecraft.tags.TagManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.HollowCore;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.registry.RegisterTagsEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mixin(TagLoader.class)
public class TagLoaderMixin {
    @Shadow
    @Final
    private String directory;

    @Inject(
            method = "build(Ljava/util/Map;)Ljava/util/Map;",
            at = @At(value = "HEAD")
    )
    private void hollowcore$load(Map<ResourceLocation, List<TagLoader.EntryWithSource>> value, CallbackInfoReturnable<Map<ResourceLocation, Collection>> cir) {
        BuiltInRegistries.REGISTRY
                .stream()
                //? if >= 1.21 {
                /*.filter(t -> Registries.tagsDirPath(t.key()).equals(directory))
                *///?} else {
                .filter(t -> TagManager.getTagDir(t.key()).equals(directory))
                //?}
                .findFirst()
                .ifPresent(reg -> EventBus.post(new RegisterTagsEvent(reg, value)));

    }
}