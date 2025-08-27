package ru.hollowhorizon.hollowengine.mixins.client;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.sugar.Local;
import kotlin.jvm.functions.Function0;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.client.render.RegisterEntityLayersDefinitions;
import java.util.HashMap;
import java.util.Map;

@Mixin(LayerDefinitions.class)
public class LayerDefinitionsMixin {
    @Inject(
            method = "createRoots",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/properties/WoodType;values()Ljava/util/stream/Stream;",
                    ordinal = 1,
                    shift = At.Shift.AFTER
            )
    )
    private static void createRoots(CallbackInfoReturnable<Map<ModelLayerLocation, LayerDefinition>> cir, @Local ImmutableMap.Builder<ModelLayerLocation, LayerDefinition> builder) {
        hc$loadLayerDefinitions(builder);
    }

    @Unique
    private static void hc$loadLayerDefinitions(ImmutableMap.Builder<ModelLayerLocation, LayerDefinition> builder) {
        var m = new HashMap<ModelLayerLocation, Function0<LayerDefinition>>();
        var event = new RegisterEntityLayersDefinitions(m);
        EventBus.post(event);

        m.forEach((k, v) -> builder.put(k, v.invoke()));
    }
}
