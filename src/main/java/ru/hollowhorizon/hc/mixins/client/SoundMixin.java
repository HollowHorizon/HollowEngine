package ru.hollowhorizon.hc.mixins.client;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.FileToIdConverter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hc.client.audio.streams.ExtendedSoundConverter;

@Mixin(Sound.class)
public class SoundMixin {

    @Mutable
    @Shadow
    @Final
    public static FileToIdConverter SOUND_LISTER;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void onInit(CallbackInfo ci) {
        SOUND_LISTER = ExtendedSoundConverter.INSTANCE;
    }
}
