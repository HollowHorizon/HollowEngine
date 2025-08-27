package ru.hollowhorizon.hc.mixins;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hc.common.events.EventBus;
import ru.hollowhorizon.hc.common.events.entity.player.PlayerInteractEvent;

@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {
    @Inject(method = "useItemOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"), cancellable = true)
    private void onRightClickBlock(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        var event = new PlayerInteractEvent.BlockInteract(player, hand, hitResult);
        EventBus.post(event);
        if (event.isCanceled()) cir.cancel();
    }

    @Inject(method = "useItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getCount()I"), cancellable = true)
    private void onRightClickItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        var event = new PlayerInteractEvent.ItemInteract(player, hand, stack);
        EventBus.post(event);
        if (event.isCanceled()) cir.cancel();
    }
}
