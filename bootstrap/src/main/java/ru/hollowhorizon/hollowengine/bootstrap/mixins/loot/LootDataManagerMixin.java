package ru.hollowhorizon.hollowengine.bootstrap.mixins.loot;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

import java.util.HashMap;
import java.util.Map;

//? if < 1.21 {
@Mixin(LootDataManager.class)
public class LootDataManagerMixin {
    @Shadow private Map<LootDataId<?>, ?> elements;

    @Inject(method = "apply", at = @At("TAIL"))
    private void hollowengine$onReload(Map<LootDataType<?>, Map<ResourceLocation, ?>> collectedElements, CallbackInfo ci) {
        var mutableElements = new HashMap<LootDataId<?>, Object>();
        elements.forEach(mutableElements::put);
        elements = mutableElements;
        BootstrapRuntimeManager.bridge().onRegisterLoot(mutableElements);
    }
}
//?}
