package ru.hollowhorizon.hc.mixins.client;

import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.Util;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.LoopingAudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hc.client.audio.streams.Mp3StreamingAudioStream;
import ru.hollowhorizon.hc.client.audio.streams.WavAudioStream;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Mixin(SoundBufferLibrary.class)
public class SoundBufferLibraryMixin {
    @Shadow
    @Final
    private Map<ResourceLocation, CompletableFuture<SoundBuffer>> cache;

    @Shadow
    @Final
    private ResourceProvider resourceManager;

    @Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
    private void onLoadSound(ResourceLocation soundID, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        var path = soundID.getPath();

        if (path.endsWith(".mp3") || path.endsWith(".wav")) {
            var future = this.cache.computeIfAbsent(soundID, resourceLocation -> CompletableFuture.supplyAsync(() -> {
                try (InputStream inputStream = this.resourceManager.open(resourceLocation)) {
                    SoundBuffer soundBuffer;

                    if (path.endsWith(".wav")) {
                        try (WavAudioStream oggAudioStream = new WavAudioStream(inputStream)) {
                            ByteBuffer byteBuffer = oggAudioStream.readAll();
                            soundBuffer = new SoundBuffer(byteBuffer, oggAudioStream.getFormat());
                        }
                    } else {
                        try (Mp3StreamingAudioStream oggAudioStream = new Mp3StreamingAudioStream(inputStream)) {
                            ByteBuffer byteBuffer = oggAudioStream.readAll();
                            soundBuffer = new SoundBuffer(byteBuffer, oggAudioStream.getFormat());
                        }
                    }
                    return soundBuffer;
                } catch (IOException iOException) {
                    throw new CompletionException(iOException);
                }
            }, Util.backgroundExecutor()));
            cir.setReturnValue(future);
        }
    }

    @Inject(method = "getStream", at = @At("HEAD"), cancellable = true)
    private void onLoadStream(ResourceLocation resourceLocation, boolean isWrapper, CallbackInfoReturnable<CompletableFuture<AudioStream>> cir) {
        var path = resourceLocation.getPath();

        if (path.endsWith(".mp3") || path.endsWith(".wav")) {
            var future = CompletableFuture.supplyAsync(() -> {
                try {
                    InputStream inputStream = this.resourceManager.open(resourceLocation);
                    if( path.endsWith(".wav")) {
                        return isWrapper ? new LoopingAudioStream(WavAudioStream::new, inputStream) : new WavAudioStream(inputStream);
                    } else {
                        return isWrapper ? new LoopingAudioStream(Mp3StreamingAudioStream::new, inputStream) : new Mp3StreamingAudioStream(inputStream);
                    }
                } catch (IOException iOException) {
                    throw new CompletionException(iOException);
                }
            }, Util.backgroundExecutor());
            cir.setReturnValue(future);
        }
    }
}
