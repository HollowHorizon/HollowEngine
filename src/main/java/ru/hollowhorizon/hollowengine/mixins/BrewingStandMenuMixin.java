package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.brew.BrewedPlayerPotionEvent;

@Mixin(targets = "net/minecraft/world/inventory/BrewingStandMenu$PotionSlot")
public class BrewingStandMenuMixin {
    @Inject(
            method = "onTake",
            at = @At(value = "RETURN")
    )
    public void onTake(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer)
            EventBus.post(new BrewedPlayerPotionEvent(player, stack));
    }
}
