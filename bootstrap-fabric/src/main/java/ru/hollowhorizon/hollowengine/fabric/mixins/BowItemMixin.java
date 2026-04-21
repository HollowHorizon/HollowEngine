package ru.hollowhorizon.hollowengine.fabric.mixins;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

@Mixin(BowItem.class)
public class BowItemMixin {
    @Redirect(method = "releaseUsing", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"))
    private float hollowengine$onArrowLoose(int charge, ItemStack stack, Level level, LivingEntity entity, int timeCharge) {
        if (!(entity instanceof Player player)) return BowItem.getPowerForTime(charge);
        var lookup = level.holderLookup(Registries.ENCHANTMENT);
        boolean flag = player.getAbilities().instabuild || EnchantmentHelper.getItemEnchantmentLevel(lookup.getOrThrow(Enchantments.INFINITY), stack) > 0;
        int updatedCharge = BootstrapRuntimeManager.bridge().onArrowLoose(stack, level, player, charge, !stack.isEmpty() || flag);
        return BowItem.getPowerForTime(updatedCharge);
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void hollowengine$onArrowNock(Level level, Player player, InteractionHand usedHand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        ItemStack replacement = BootstrapRuntimeManager.bridge().onArrowNock(itemStack, level, player, usedHand);
        if (replacement != null) {
            cir.setReturnValue(InteractionResultHolder.pass(replacement));
        }
    }
}
