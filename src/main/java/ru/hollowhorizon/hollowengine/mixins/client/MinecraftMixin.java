/*
 * MIT License
 *
 * Copyright (c) 2024 HollowHorizon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ru.hollowhorizon.hollowengine.mixins.client;

import kotlinx.coroutines.CoroutineDispatcher;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineScopeKt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.client.kool.KoolBuffersKt;
import ru.hollowhorizon.hollowengine.client.kool.KoolManager;
import ru.hollowhorizon.hollowengine.common.coroutines.ClientDispatcher;
import ru.hollowhorizon.hollowengine.common.coroutines.SingleThreadDispatcher;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.client.render.RenderTickEvent;
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks;

import static kotlinx.coroutines.SupervisorKt.SupervisorJob;

@Mixin(Minecraft.class)
public class MinecraftMixin implements ClientDispatcher {
    @Unique
    private SingleThreadDispatcher hollowcore$dispatcher;
    @Unique
    private CoroutineScope hollowcore$coroutineScope;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(GameConfig gameConfig, CallbackInfo ci) {
        hollowcore$dispatcher = new SingleThreadDispatcher("MinecraftServer.dispatcher");
        hollowcore$coroutineScope = CoroutineScopeKt.CoroutineScope(SupervisorJob(null).plus(hollowcore$dispatcher));
    }

    @Inject(method = "runTick", at = @At("HEAD"))
    protected void hollowcore$runTasks(CallbackInfo ci) {
        hollowcore$dispatcher.runTasks();
    }

    //? if >= 1.21 {
    /*@Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    *///?} else {
    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V"))
    //?}
    protected void hollowcore$renderTick$before(CallbackInfo ci) {
        EventBus.post(new RenderTickEvent.Pre(JavaHacks.forceCast(this)));
    }
    //? if >= 1.21 {
    /*@Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V", shift = At.Shift.AFTER))
    *///?} else {
    @Inject(method = "runTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(FJZ)V", shift = At.Shift.AFTER))
    //?}
    protected void hollowcore$renderTick$after(CallbackInfo ci) {
        EventBus.post(new RenderTickEvent.Post(JavaHacks.forceCast(this)));
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void cancelCoroutineScope(CallbackInfo ci) {
        CoroutineScopeKt.cancel(hollowcore$coroutineScope, null);

        hollowcore$dispatcher.runTasks();
    }

    @Inject(method = "stop", at = @At("RETURN"))
    private void shutdownDispatcher(CallbackInfo ci) {
        hollowcore$dispatcher.shutdown();
    }

    @Inject(method = "resizeDisplay", at = @At("RETURN"))
    private void resizeCapturedDepthBuffer(CallbackInfo ci) {
        var init = KoolManager.INSTANCE;
        final var window = Minecraft.getInstance().getWindow();
        KoolBuffersKt.getGuiFramebuffer().resize(window.getWidth(), window.getHeight(), Minecraft.ON_OSX);
        KoolBuffersKt.onResize(window.getWidth(), window.getHeight());
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
