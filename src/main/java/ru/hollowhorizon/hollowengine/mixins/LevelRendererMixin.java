package ru.hollowhorizon.hollowengine.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.client.gui.modificators.BiomeModificator;
import ru.hollowhorizon.hollowengine.client.gui.modificators.SkyBoxRenderer;

@Mixin(value = LevelRenderer.class)
public class LevelRendererMixin {

    @ModifyConstant(
            method = "renderSky",
            constant = @Constant(floatValue = 30.0F)
    )
    private float changeSunSize(float original) {
        if (!BiomeModificator.INSTANCE.getEnable().get()) return original;
        return BiomeModificator.INSTANCE.getSunSize()[0];
    }

    @ModifyConstant(
            method = "renderSky",
            constant = @Constant(floatValue = 20.0F)
    )
    private float changeMoonSize(float original) {
        if (!BiomeModificator.INSTANCE.getEnable().get()) return original;
        return BiomeModificator.INSTANCE.getMoonSize()[0];
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    //? if >=1.21 {
    private void renderCustomSkyboxes(Matrix4f frustumMatrix, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
    //?} elif >=1.20.1 {
    /*private void renderCustomSkyboxes(PoseStack matrixStack, Matrix4f projectionMatrix, float partialTick, Camera camera, boolean isFoggy, Runnable skyFogSetup, CallbackInfo ci) {
    *///?} else {
    /*private void renderCustomSkyboxes(PoseStack matrixStack, com.mojang.math.Matrix4f projectionMatrix, float partialTick, Camera camera, boolean bl, Runnable skyFogSetup, CallbackInfo ci) {
        *///?}

        if (BiomeModificator.INSTANCE.getEnableSkybox().get()) {
            //? if >=1.21 {
            PoseStack matrixStack = new PoseStack();
            matrixStack.mulPose(frustumMatrix);
            //?}
            SkyBoxRenderer.INSTANCE.render(matrixStack);
            ci.cancel();
        }
    }
}
