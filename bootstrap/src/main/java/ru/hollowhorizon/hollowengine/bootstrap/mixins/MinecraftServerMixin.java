package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import com.mojang.datafixers.DataFixer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

import java.net.Proxy;

@Mixin(value = MinecraftServer.class, priority = 993)
public abstract class MinecraftServerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer fixerUpper, Services services, ChunkProgressListenerFactory progressListenerFactory, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onServerCreated((MinecraftServer) (Object) this, serverThread, storageSource.getLevelPath(LevelResource.ROOT));
    }

    @Inject(method = "runServer", at = @At("HEAD"))
    private void onRun(CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onServerStarting((MinecraftServer) (Object) this);
    }

    @Inject(method = "createLevels", at = @At("TAIL"))
    private void onCreateLevels(ChunkProgressListener listener, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onServerLevelsCreated((MinecraftServer) (Object) this);
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void onTickServer(CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onServerTick((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void beforeStopServer(CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onServerStopping((MinecraftServer) (Object) this);
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void afterStopServer(CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onServerStopped((MinecraftServer) (Object) this);
    }
}
