package ru.hollowhorizon.hollowengine.bootstrap.mixins.fabric;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

@Mixin(BlockItem.class)
public class BlockItemMixin {
    @Inject(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/context/BlockPlaceContext;getClickedPos()Lnet/minecraft/core/BlockPos;"), locals = LocalCapture.CAPTURE_FAILHARD, cancellable = true)
    private void hollowengine$onPlace(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir, BlockPlaceContext blockPlaceContext, BlockState blockState) {
        var player = context.getPlayer();
        if (player == null) return;
        if (BootstrapRuntimeManager.bridge().onBlockPlaced(player, blockState, context.getClickedPos())) {
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
