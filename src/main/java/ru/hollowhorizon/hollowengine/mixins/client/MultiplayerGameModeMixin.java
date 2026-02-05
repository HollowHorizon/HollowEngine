package ru.hollowhorizon.hollowengine.mixins.client;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent;

@Mixin(MultiPlayerGameMode.class)
public class MultiplayerGameModeMixin {
    @Inject(method = "performUseItemOn", at = @At(value = "HEAD"), cancellable = true)
    private void onRightClickBlock(LocalPlayer player, InteractionHand hand, BlockHitResult result, CallbackInfoReturnable<InteractionResult> cir) {
        var event = new PlayerInteractEvent.BlockInteract(player, hand, result);
        EventBus.post(event);
        if (event.isCanceled()) cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "interact", at = @At(value = "HEAD"), cancellable = true)
    private void onInteractEntity(Player player, Entity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        var event = new PlayerInteractEvent.EntityInteract(player, hand, target);
        EventBus.post(event);
        if (event.isCanceled()) cir.setReturnValue(InteractionResult.FAIL);
    }

    @Inject(method = "useItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;startPrediction(Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/multiplayer/prediction/PredictiveAction;)V"), cancellable = true)
    private void onInteractItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        var event = new PlayerInteractEvent.ItemInteract(player, hand, player.getItemInHand(hand));
        EventBus.post(event);
        if (event.isCanceled()) cir.setReturnValue(InteractionResult.FAIL);
    }
}
