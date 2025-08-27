package ru.hollowhorizon.hollowengine.mixins;

import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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
import ru.hollowhorizon.hollowengine.HollowCore;
import ru.hollowhorizon.hollowengine.api.ICapabilityDispatcher;
import ru.hollowhorizon.hollowengine.api.ICapabilityDispatcherKt;
import ru.hollowhorizon.hollowengine.common.capabilities.CapabilityInstance;
import ru.hollowhorizon.hollowengine.common.coroutines.ServerDispatcher;
import ru.hollowhorizon.hollowengine.common.coroutines.SingleThreadDispatcher;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.level.LevelEvent;
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormatKt;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static kotlinx.coroutines.SupervisorKt.SupervisorJob;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements ICapabilityDispatcher, ServerDispatcher {
    @Unique
    private SingleThreadDispatcher hollowcore$dispatcher;
    @Unique
    private CoroutineScope hollowcore$coroutineScope;

    @Unique
    private final Map<String, CapabilityInstance> hollowCore$capabilities = new Object2ObjectOpenHashMap<>();

    @NotNull
    @Override
    public Map<String, CapabilityInstance> getCapabilities() {
        return hollowCore$capabilities;
    }


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
        hollowcore$dispatcher = new SingleThreadDispatcher("MinecraftServer.dispatcher");
        hollowcore$coroutineScope = CoroutineScopeKt.CoroutineScope(SupervisorJob(null).plus(hollowcore$dispatcher));

        ICapabilityDispatcherKt.initialize(this);

        //? if fabric {
        var file = storageSource.getIconFile().get().getParent().resolve("server_capability.dat").toFile();
        //?} else {
        /*var file = storageSource.getWorldDir().resolve(storageSource.getLevelId()).resolve("server_capability.dat").toFile();
         *///?}
        if (file.exists()) {
            try {
                var stream = new FileInputStream(file);
                var tag = NBTFormatKt.loadAsNBT(stream);
                stream.close();
                ICapabilityDispatcherKt.deserializeCapabilities(this, (CompoundTag) tag);
            } catch (IOException e) {
                HollowCore.LOGGER.error("Can't load {}", file.getName(), e);
            }
        }
    }

    @Inject(method = "createLevels", at = @At("TAIL"))
    private void onSave(ChunkProgressListener $$0, CallbackInfo ci) {
        Registry<LevelStem> registry = registries.compositeAccess().registryOrThrow(Registries.LEVEL_STEM);
        for (ResourceKey<LevelStem> key : registry.registryKeySet()) {
            var level = levels.get(key);
            EventBus.post(new LevelEvent.Load(level));
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void onSave(CallbackInfo ci) {
        //? if fabric {
        var file = storageSource.getIconFile().get().getParent().resolve("server_capability.dat").toFile();
        //?} else {
        /*var file = storageSource.getWorldDir().resolve(storageSource.getLevelId()).resolve("server_capability.dat").toFile();
         *///?}

        try {
            if (!file.exists()) file.createNewFile();
            var output = new FileOutputStream(file);
            var tag = new CompoundTag();
            ICapabilityDispatcherKt.serializeCapabilities(this, tag);
            NBTFormatKt.save(tag, output);
        } catch (IOException e) {
            HollowCore.LOGGER.error("Can't load {}", file.getName(), e);
        }
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    protected void essential$runTasks(CallbackInfo ci) {
        hollowcore$dispatcher.runTasks();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void cancelCoroutineScope(CallbackInfo ci) {
        CoroutineScopeKt.cancel(hollowcore$coroutineScope, null);

        hollowcore$dispatcher.runTasks();
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
