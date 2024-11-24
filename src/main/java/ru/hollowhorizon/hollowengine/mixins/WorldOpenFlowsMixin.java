package ru.hollowhorizon.hollowengine.mixins;

import com.mojang.serialization.Lifecycle;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldOpenFlows.class)
public class WorldOpenFlowsMixin {
    //? if fabric {
    /*@Redirect(
            method = "doLoadLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/WorldData;worldGenSettingsLifecycle()Lcom/mojang/serialization/Lifecycle;")
    )*/
    //?} else {
    @Redirect(
            method = "doLoadLevel(Lnet/minecraft/client/gui/screens/Screen;Ljava/lang/String;ZZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/WorldData;worldGenSettingsLifecycle()Lcom/mojang/serialization/Lifecycle;")
    )
    //?}
    private Lifecycle removeAdviceOnLoad(WorldData instance) {
        return Lifecycle.stable();
    }

    @ModifyVariable(
            method = "confirmWorldCreation", at = @At("HEAD"),
            argsOnly = true, index = 4
    )
    private static boolean removeAdviceOnCreation(boolean original) {
        return true;
    }
}
