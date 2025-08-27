package ru.hollowhorizon.hc.mixins.loot;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.server.commands.LootCommand;
import org.spongepowered.asm.mixin.Mixin;

//? if >= 1.21 {
@Mixin(LootCommand.class)
public class LootDataManagerMixin {}
//?} else {

/*import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hc.common.events.EventBus;
import ru.hollowhorizon.hc.common.events.registry.RegisterLootEvent;

import java.util.HashMap;
import java.util.Map;

@Mixin(LootDataManager.class)
public class LootDataManagerMixin {
    @Shadow
    private Map<LootDataId<?>, ?> elements;

    @Inject(method = "apply", at = @At("TAIL"))
    private void onReload(Map<LootDataType<?>, Map<ResourceLocation, ?>> collectedElements, CallbackInfo ci) {
        elements = new HashMap<>(elements); // Makes map mutable
        EventBus.post(new RegisterLootEvent(elements));
    }
}

*///?}