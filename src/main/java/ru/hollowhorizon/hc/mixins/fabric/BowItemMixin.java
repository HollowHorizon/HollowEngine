package ru.hollowhorizon.hc.mixins.fabric;

//? if >= 1.21
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
import ru.hollowhorizon.hc.common.events.EventBus;
import ru.hollowhorizon.hc.common.events.item.ArrowEvent;

@Mixin(BowItem.class)
public class BowItemMixin {
    //? if fabric {
    @Redirect(
            method = "releaseUsing",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/BowItem;getPowerForTime(I)F"
            )
    )
    private float onArrowLoose(int charge, ItemStack stack, Level level, LivingEntity entity, int timeCharge) {
        if (!(entity instanceof Player player)) return BowItem.getPowerForTime(charge);

        //? >= 1.21 {
        var enchantment = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.INFINITY);
        boolean flag = player.getAbilities().instabuild || EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack) > 0;
        //?} else {
        /*boolean flag = player.getAbilities().instabuild || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, stack) > 0;
        *///?}

        var event = new ArrowEvent.Loose(stack, level, player, charge, !stack.isEmpty() || flag);
        EventBus.post(event);
        if(event.isCanceled()) event.setCharge(-10); // Will be canceled by game

        return BowItem.getPowerForTime(event.getCharge());
    }

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void onArrowNock(Level level, Player player, InteractionHand usedHand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack itemstack = player.getItemInHand(usedHand);
        boolean flag = !player.getProjectile(itemstack).isEmpty();
        ArrowEvent.Nock ret = new ArrowEvent.Nock(itemstack, level, player, usedHand, flag);
        EventBus.post(ret);
        if (!itemstack.equals(ret.getStack())) cir.setReturnValue(InteractionResultHolder.pass(itemstack));
    }
    //?}
}
