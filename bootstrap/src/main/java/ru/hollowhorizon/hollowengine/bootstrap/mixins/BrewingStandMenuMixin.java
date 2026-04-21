package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

@Mixin(targets = "net/minecraft/world/inventory/BrewingStandMenu$PotionSlot")
public class BrewingStandMenuMixin {
    @Inject(method = "onTake", at = @At("RETURN"))
    private void hollowengine$onTake(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer) {
            BootstrapRuntimeManager.bridge().onBrewedPlayerPotion(player, stack);
        }
    }
}
