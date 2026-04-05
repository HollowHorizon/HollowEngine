package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

@Mixin(LayerDefinitions.class)
public class LayerDefinitionsMixin {
    @Inject(
        method = "createRoots",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/properties/WoodType;values()Ljava/util/stream/Stream;", ordinal = 1, shift = At.Shift.AFTER)
    )
    private static void createRoots(CallbackInfoReturnable<Map<ModelLayerLocation, LayerDefinition>> cir, @Local ImmutableMap.Builder<ModelLayerLocation, LayerDefinition> builder) {
        hollowengine$loadLayerDefinitions(builder);
    }

    @Unique
    private static void hollowengine$loadLayerDefinitions(ImmutableMap.Builder<ModelLayerLocation, LayerDefinition> builder) {
        Map<ModelLayerLocation, Supplier<LayerDefinition>> definitions = new HashMap<>();
        BootstrapRuntimeManager.bridge().onRegisterLayerDefinitions(definitions);
        definitions.forEach((location, supplier) -> builder.put(location, supplier.get()));
    }
}
