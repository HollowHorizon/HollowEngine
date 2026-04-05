package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

@Mixin(BrewingStandBlockEntity.class)
public class BrewingStandBlockEntityMixin {
    @Inject(method = "doBrew", at = @At("HEAD"), cancellable = true)
    private static void hollowengine$doBrew(Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci) {
        if (BootstrapRuntimeManager.bridge().onBrewPotionPre(items)) ci.cancel();
    }

    @Inject(
            method = "doBrew",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/NonNullList;get(I)Ljava/lang/Object;",
                    ordinal = 0,
                    shift = At.Shift.AFTER
            )
    )
    private static void hollowengine$doBrewInv(Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onBrewPotionPost(items);
    }
}
