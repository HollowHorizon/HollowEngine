package ru.hollowhorizon.hollowengine.mixins;

import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static kotlinx.coroutines.CoroutineScopeKt.CoroutineScope;
import static kotlinx.coroutines.CoroutineScopeKt.cancel;
import static kotlinx.coroutines.SupervisorKt.SupervisorJob;
import ru.hollowhorizon.hollowengine.common.scripting.scene.MinecraftServerExt;import ru.hollowhorizon.hollowengine.common.scripting.scene.SingleThreadDispatcher;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements MinecraftServerExt {
    @Unique
    private SingleThreadDispatcher hollowcore$dispatcher;
    @Unique
    private CoroutineScope hollowcore$coroutineScope;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void init(CallbackInfo ci) {
        hollowcore$dispatcher = new SingleThreadDispatcher("MinecraftServer.dispatcher");
        hollowcore$coroutineScope = CoroutineScope(SupervisorJob(null).plus(hollowcore$dispatcher));
    }

    @Inject(method = "tickServer", at = @At("HEAD"))
    protected void essential$runTasks(CallbackInfo ci) {
        hollowcore$dispatcher.runTasks();
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void cancelCoroutineScope(CallbackInfo ci) {
        cancel(hollowcore$coroutineScope, null);

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
