package ru.hollowhorizon.hollowengine.mixins.components;

import com.mojang.datafixers.DataFixer;
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
import ru.hollowhorizon.hollowengine.common.components.ComponentContainer;
import ru.hollowhorizon.hollowengine.common.components.ComponentContainerKt;
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher;

import java.net.Proxy;


@Mixin(value = MinecraftServer.class, priority = 999)
public class MinecraftServerMixin implements ComponentDispatcher {
    @Shadow @Final protected LevelStorageSource.LevelStorageAccess storageSource;
    @Unique
    private final ComponentContainer hollowengine$container = new ComponentContainer(this);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(Thread serverThread, LevelStorageSource.LevelStorageAccess storageSource, PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer fixerUpper, Services services, ChunkProgressListenerFactory progressListenerFactory, CallbackInfo ci) {
        //? if fabric {
        var file = storageSource.getIconFile().get().getParent().resolve("server-components.dat").toFile();
        //?} else {
        /*var file = storageSource.getWorldDir().resolve(storageSource.getLevelId()).resolve("server_capability.dat").toFile();
         *///?}

        if (file.exists()) {
            ComponentContainerKt.load(hollowengine$container, file);
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void onSave(CallbackInfo ci) {
        //? if fabric {
        var file = storageSource.getIconFile().get().getParent().resolve("server-components.dat").toFile();
        //?} else {
        /*var file = storageSource.getWorldDir().resolve(storageSource.getLevelId()).resolve("server_capability.dat").toFile();
         *///?}

        ComponentContainerKt.save(hollowengine$container, file);
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    protected void essential$runTasks(CallbackInfo ci) {
        hollowengine$container.update();
    }

    @Override
    public @NotNull ComponentContainer getContainer() {
        return hollowengine$container;
    }
}
