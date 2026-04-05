package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

@Mixin(Main.class)
public class DebugMainMixin {
    @Inject(method = "main", at = @At("HEAD"), remap = false)
    private static void preMain(CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onDebugClientMain();
    }
}
