package ru.hollowhorizon.hollowengine.mixins;

import de.fabmax.kool.Clipboard;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Clipboard.class, remap = false)
public class ClipboardMixin {
    @Inject(method = "copyToClipboard", at = @At("HEAD"), cancellable = true)
    private void onCopy(String string, CallbackInfo ci) {
        Minecraft.getInstance().keyboardHandler.setClipboard(string);
        ci.cancel();
    }

    @Inject(method = "getStringFromClipboard", at = @At("HEAD"), cancellable = true)
    private void onPaste(Function1<? super String, Unit> receiver, CallbackInfo ci) {
        var clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
        String text = clipboard.isEmpty() ? null : clipboard;
        receiver.invoke(text);
        ci.cancel();
    }
}
