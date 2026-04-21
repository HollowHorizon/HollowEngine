package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import com.google.common.collect.ImmutableMap;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.world.level.block.SkullBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

import java.util.Map;

@Mixin(SkullBlockRenderer.class)
public class SkullBlockRendererMixin {
    @Inject(method = "createSkullRenderers", at = @At("TAIL"))
    private static void createSkullRenderers(EntityModelSet entityModelSet, CallbackInfoReturnable<Map<SkullBlock.Type, SkullModelBase>> cir, @Local ImmutableMap.Builder<SkullBlock.Type, SkullModelBase> builder) {
        BootstrapRuntimeManager.bridge().onCreateSkullModels(builder, entityModelSet);
    }
}
