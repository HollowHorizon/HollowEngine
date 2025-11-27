package ru.hollowhorizon.hollowengine.mixins.fabric;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.blocks.BlockEvent;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    //? if fabric {
    @Inject(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/context/BlockPlaceContext;getClickedPos()Lnet/minecraft/core/BlockPos;"), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir, BlockPlaceContext blockPlaceContext, BlockState blockState) {
        var player = context.getPlayer();
        if(player == null) return;
        var event = new BlockEvent.Placed(player, blockState, context.getClickedPos());
        EventBus.post(event);
        if(event.isCanceled()) cir.setReturnValue(InteractionResult.FAIL);
    }
    //?}
}
