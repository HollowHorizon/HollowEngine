package ru.hollowhorizon.hollowengine.bridge.mixins.client;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface LevelRendererInvoker {
    @Invoker("needsUpdate")
    void hollowengine$needsUpdate();
}
