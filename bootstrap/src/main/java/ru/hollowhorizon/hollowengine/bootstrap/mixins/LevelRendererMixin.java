package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow @Nullable private ClientLevel level;

    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 30.0F))
    private float hollowengine$changeSunSize(float original) {
        return level == null ? original : BootstrapRuntimeManager.bridge().getSkySunSize(level, original);
    }

    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 20.0F))
    private float hollowengine$changeMoonSize(float original) {
        return level == null ? original : BootstrapRuntimeManager.bridge().getSkyMoonSize(level, original);
    }
}
