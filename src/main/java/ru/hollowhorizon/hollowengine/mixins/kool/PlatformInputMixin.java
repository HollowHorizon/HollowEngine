package ru.hollowhorizon.hollowengine.mixins.kool;

import de.fabmax.kool.input.PlatformInput;
import de.fabmax.kool.input.PlatformInput_desktopKt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.client.kool.window.MCInput;

@Mixin(value = PlatformInput_desktopKt.class, remap = false)
public class PlatformInputMixin {

    @Inject(method = "PlatformInput", at = @At("HEAD"), cancellable = true)
    private static void onGet(CallbackInfoReturnable<PlatformInput> cir) {
        cir.setReturnValue(new MCInput());
    }
}
