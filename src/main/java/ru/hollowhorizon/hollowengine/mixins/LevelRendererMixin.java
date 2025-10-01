package ru.hollowhorizon.hollowengine.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.client.render.SkyRenderEvent;

@Mixin(value = LevelRenderer.class)
public class LevelRendererMixin {

    @Shadow @Nullable private ClientLevel level;

    @ModifyConstant(
            method = "renderSky",
            constant = @Constant(floatValue = 30.0F)
    )
    private float changeSunSize(float original) {
        if (level != null) {
            var event = new SkyRenderEvent.SunSize(level, original);
            EventBus.post(event);
            return event.getSunSize();
        }
        return original;
    }

    @ModifyConstant(
            method = "renderSky",
            constant = @Constant(floatValue = 20.0F)
    )
    private float changeMoonSize(float original) {
        if (level != null) {
            var event = new SkyRenderEvent.MoonSize(level, original);
            EventBus.post(event);
            return event.getMoonSize();
        }
        return original;
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void renderCustomSkyboxes(PoseStack matrixStack, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
    }
}
