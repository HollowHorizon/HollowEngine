package ru.hollowhorizon.hollowengine.mixins;

import com.mojang.datafixers.DataFixer;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.HollowCore;
import ru.hollowhorizon.hollowengine.common.coroutines.ServerDispatcher;
import ru.hollowhorizon.hollowengine.common.coroutines.ServerRuntimeContext;
import ru.hollowhorizon.hollowengine.common.coroutines.ServerRuntimeContextProvider;
import ru.hollowhorizon.hollowengine.common.coroutines.SingleThreadDispatcher;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.level.LevelEvent;
import ru.hollowhorizon.hollowengine.common.events.server.ServerEvent;
import ru.hollowhorizon.hollowengine.common.utils.ForgeKotlinKt;
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormatKt;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static kotlinx.coroutines.SupervisorKt.SupervisorJob;

@Mixin(value = MinecraftServer.class, priority = 993)
public abstract class MinecraftServerMixin implements ServerDispatcher, ServerRuntimeContextProvider {
    @Unique
    private static final String HOLLOWENGINE_RUNTIME_FILE = "hollowengine-server-runtime.dat";
    @Unique
    private SingleThreadDispatcher hollowcore$dispatcher;
    @Unique
    private CoroutineScope hollowcore$coroutineScope;
    @Unique
    private ServerRuntimeContext hollowengine$serverRuntimeContext;
    @Unique
    private Path hollowengine$runtimePath;
    @Unique
    private int hollowengine$runtimeAutosaveTicks;

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
        hollowengine$serverRuntimeContext = new ServerRuntimeContext((MinecraftServer) (Object) this);
        hollowengine$runtimePath = storageSource.getLevelPath(LevelResource.ROOT).resolve("data").resolve(HOLLOWENGINE_RUNTIME_FILE);
    }

    @Inject(method = "runServer", at = @At("HEAD"))
    private void onRun(CallbackInfo ci) {
        ForgeKotlinKt.setCurrentServer((MinecraftServer) (Object) this);
        EventBus.post(new ServerEvent.Starting((MinecraftServer) (Object) this));
    }

    @Inject(method = "createLevels", at = @At("TAIL"))
    private void onCreateLevels(ChunkProgressListener $$0, CallbackInfo ci) {
        hollowengine$loadRuntimeContext();
        Registry<LevelStem> registry = registries.compositeAccess().registryOrThrow(Registries.LEVEL_STEM);
        for (ResourceKey<LevelStem> key : registry.registryKeySet()) {
            var level = levels.get(key);
            EventBus.post(new LevelEvent.Load(level));
        }
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    protected void essential$runTasks(CallbackInfo ci) {
        hollowcore$dispatcher.runTasks();
        if (hollowengine$serverRuntimeContext.isDirty()) {
            hollowengine$runtimeAutosaveTicks++;
            if (hollowengine$runtimeAutosaveTicks >= 200) {
                hollowengine$saveRuntimeContext();
            }
        } else {
            hollowengine$runtimeAutosaveTicks = 0;
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void beforeStopServer(CallbackInfo ci) {
        hollowengine$saveRuntimeContext();
        EventBus.post(new ServerEvent.Stoping((MinecraftServer) (Object) this));
    }

    @Inject(method = "stopServer", at = @At("RETURN"))
    private void shutdownDispatcher(CallbackInfo ci) {
        CoroutineScopeKt.cancel(hollowcore$coroutineScope, null);
        hollowcore$dispatcher.runTasks();
        hollowcore$dispatcher.shutdown();
    }

    @Unique
    private void hollowengine$loadRuntimeContext() {
        if (hollowengine$runtimePath == null || !Files.exists(hollowengine$runtimePath)) return;
        try (InputStream stream = Files.newInputStream(hollowengine$runtimePath)) {
            Tag tag = NBTFormatKt.loadAsNBT(stream);
            if (tag instanceof CompoundTag compoundTag) {
                hollowengine$serverRuntimeContext.deserialize(compoundTag);
            }
        } catch (Exception e) {
            HollowCore.LOGGER.error("Failed to load HollowEngine server runtime from {}", hollowengine$runtimePath, e);
        }
    }

    @Unique
    private void hollowengine$saveRuntimeContext() {
        if (hollowengine$runtimePath == null || !hollowengine$serverRuntimeContext.isDirty()) return;
        try {
            Files.createDirectories(hollowengine$runtimePath.getParent());
            CompoundTag tag = new CompoundTag();
            hollowengine$serverRuntimeContext.serialize(tag);
            try (OutputStream stream = Files.newOutputStream(hollowengine$runtimePath)) {
                NBTFormatKt.save(tag, stream);
            }
            hollowengine$serverRuntimeContext.clearDirty();
            hollowengine$runtimeAutosaveTicks = 0;
        } catch (Exception e) {
            HollowCore.LOGGER.error("Failed to save HollowEngine server runtime to {}", hollowengine$runtimePath, e);
        }
    }

    @Override
    public @NotNull CoroutineDispatcher getHollowcore$dispatcher() {
        return hollowcore$dispatcher;
    }

    @Override
    public @NotNull CoroutineScope getHollowcore$coroutineScope() {
        return hollowcore$coroutineScope;
    }

    @Override
    public @NotNull ServerRuntimeContext getHollowengine$serverRuntimeContext() {
        return hollowengine$serverRuntimeContext;
    }
}
