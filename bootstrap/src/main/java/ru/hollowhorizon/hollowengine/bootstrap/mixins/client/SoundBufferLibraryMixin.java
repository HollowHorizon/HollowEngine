package ru.hollowhorizon.hollowengine.bootstrap.mixins.client;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {
    @Shadow @Final private Map<ResourceLocation, CompletableFuture<SoundBuffer>> cache;
    @Shadow @Final private ResourceProvider resourceManager;

    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void onLoadSound(ResourceLocation soundId, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        CompletableFuture<SoundBuffer> future = BootstrapRuntimeManager.bridge().onLoadCompleteSound(soundId, resourceManager, cache);
        if (future != null) {
            cir.setReturnValue(future);
        }
    }

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void onLoadStream(ResourceLocation soundId, boolean isWrapper, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        CompletableFuture<AudioStream> future = BootstrapRuntimeManager.bridge().onLoadStreamSound(soundId, resourceManager, isWrapper);
        if (future != null) {
            cir.setReturnValue(future);
        }
    }
}
