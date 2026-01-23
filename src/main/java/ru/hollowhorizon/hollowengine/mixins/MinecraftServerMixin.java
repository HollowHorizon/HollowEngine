package ru.hollowhorizon.hollowengine.mixins;

import com.mojang.datafixers.DataFixer;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.coroutines.ServerDispatcher;
import ru.hollowhorizon.hollowengine.common.coroutines.SingleThreadDispatcher;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.level.LevelEvent;
import ru.hollowhorizon.hollowengine.common.events.server.ServerEvent;
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager;
import ru.hollowhorizon.hollowengine.common.geary.GearyMinecraftBootstrap;
import ru.hollowhorizon.hollowengine.common.utils.ForgeKotlinKt;

import java.net.Proxy;
import java.util.Map;

import static kotlinx.coroutines.SupervisorKt.SupervisorJob;

@Mixin(value = MinecraftServer.class, priority = 993)
public abstract class MinecraftServerMixin implements ServerDispatcher {
    @Unique
    private SingleThreadDispatcher hollowcore$dispatcher;
    @Unique
    private CoroutineScope hollowcore$coroutineScope;
    @Unique GearyMinecraftBootstrap hollowcore$geary;

    @Shadow
    @Final
    protected LevelStorageSource.LevelStorageAccess storageSource;

    @Shadow
    @Final
    private Map<ResourceKey<Level>, ServerLevel> levels;

    @Shadow
    @Final
    private LayeredRegistryAccess<RegistryLayer> registries;

    @Shadow
    public abstract LayeredRegistryAccess<RegistryLayer> registries();


    @Inject(method = "<init>", at=@At("TAIL"))
    private void onInit(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer fixerUpper, Services services, ChunkProgressListenerFactory progressListenerFactory, CallbackInfo ci) {
        hollowcore$dispatcher = new SingleThreadDispatcher("MinecraftServer.dispatcher", serverThread);
        hollowcore$coroutineScope = CoroutineScopeKt.CoroutineScope(SupervisorJob(null).plus(hollowcore$dispatcher));
        hollowcore$geary = new GearyMinecraftBootstrap((MinecraftServer) (Object) this, DirectoryManager.GEARY);
    }

    @Inject(method = "runServer", at = @At("HEAD"))
    private void onRun(CallbackInfo ci) {
        ForgeKotlinKt.setCurrentServer((MinecraftServer) (Object) this);
        EventBus.post(new ServerEvent.Starting((MinecraftServer) (Object) this));
        hollowcore$geary.onServerStarting();
    }

    @Inject(method = "createLevels", at = @At("TAIL"))
    private void onSave(ChunkProgressListener $$0, CallbackInfo ci) {
        Registry<LevelStem> registry = registries.compositeAccess().registryOrThrow(Registries.LEVEL_STEM);
        for (ResourceKey<LevelStem> key : registry.registryKeySet()) {
            var level = levels.get(key);
            EventBus.post(new LevelEvent.Load(level));
        }
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    protected void essential$runTasks(CallbackInfo ci) {
        hollowcore$geary.onTick();
        hollowcore$dispatcher.runTasks();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void cancelCoroutineScope(CallbackInfo ci) {
        CoroutineScopeKt.cancel(hollowcore$coroutineScope, null);

        hollowcore$dispatcher.runTasks();
        EventBus.post(new ServerEvent.Stoping((MinecraftServer) (Object) this));
        hollowcore$geary.onServerStopping();
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void shutdownDispatcher(CallbackInfo ci) {
        hollowcore$dispatcher.shutdown();
    }

    @Override
    public @NotNull CoroutineDispatcher getHollowcore$dispatcher() {
        return hollowcore$dispatcher;
    }

    @Override
    public @NotNull CoroutineScope getHollowcore$coroutineScope() {
        return hollowcore$coroutineScope;
    }
}
