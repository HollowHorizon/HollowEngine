package ru.hollowhorizon.hollowengine.mixins.components;

import com.mojang.datafixers.DataFixer;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.components.Component;
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher;
import ru.hollowhorizon.hollowengine.common.components.lifecycle.ComponentSaving;
import ru.hollowhorizon.hollowengine.common.components.lifecycle.ComponentSyncingKt;

import java.net.Proxy;
import java.util.Map;


@Mixin(value = MinecraftServer.class, priority = 999)
public class MinecraftServerMixin implements ComponentDispatcher {
    @Shadow @Final protected LevelStorageSource.LevelStorageAccess storageSource;
    @Unique
    private final Map<ResourceLocation, Component<?>> hollowCore$components = new Object2ObjectOpenHashMap<>();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer fixerUpper, Services services, ChunkProgressListenerFactory progressListenerFactory, CallbackInfo ci) {
        //? if fabric {
        var file = storageSource.getIconFile().get().getParent().resolve("server-components.dat").toFile();
        //?} else {
        /*var file = storageSource.getWorldDir().resolve(storageSource.getLevelId()).resolve("server_capability.dat").toFile();
         *///?}

        if (file.exists()) {
            ComponentSaving.save(this, file);
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void onSave(CallbackInfo ci) {
        //? if fabric {
        var file = storageSource.getIconFile().get().getParent().resolve("server-components.dat").toFile();
        //?} else {
        /*var file = storageSource.getWorldDir().resolve(storageSource.getLevelId()).resolve("server_capability.dat").toFile();
         *///?}

        ComponentSaving.save(this, file);
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    protected void essential$runTasks(CallbackInfo ci) {
        ComponentSyncingKt.onTick(this);
    }

    @Override
    public @NotNull Map<@NotNull ResourceLocation, @NotNull Component<?>> getHollowcore$components() {
        return hollowCore$components;
    }
}
