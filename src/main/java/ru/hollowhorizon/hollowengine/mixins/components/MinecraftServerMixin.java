package ru.hollowhorizon.hollowengine.mixins.components;

import net.minecraft.server.MinecraftServer;
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


@Mixin(value = MinecraftServer.class, priority = 990)
public class MinecraftServerMixin implements ComponentDispatcher {
    @Unique
    private final ComponentContainer hollowengine$container = new ComponentContainer(this);
    @Shadow
    @Final
    protected LevelStorageSource.LevelStorageAccess storageSource;

    @Inject(method = "runServer", at = @At("HEAD"))
    private void onInit(CallbackInfo ci) {
        //? if fabric {
        var file = storageSource.getIconFile().get().getParent().resolve("server-components.dat").toFile();
        //?} else {
        /*var file = storageSource.getWorldDir().resolve(storageSource.getLevelId()).resolve("server_capability.dat").toFile();
         *///?}

        ComponentContainerKt.load(hollowengine$container, file);
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
