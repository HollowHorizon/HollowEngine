package ru.hollowhorizon.hollowengine.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.brew.BrewPotionEvent;

@Mixin(BrewingStandBlockEntity.class)
public class BrewingStandBlockEntityMixin {
    @Inject(method = "doBrew", at = @At("HEAD"), cancellable = true)
    private static void doBrew(Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci) {
        if (hc$attemptBrew(items)) ci.cancel();
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
    private static void doBrewInv(Level level, BlockPos pos, NonNullList<ItemStack> items, CallbackInfo ci) {
        hc$potionBrewed(items);
    }

    @Unique
    private static boolean hc$attemptBrew(NonNullList<ItemStack> stacks) {
        var tmp = NonNullList.withSize(stacks.size(), ItemStack.EMPTY);
        for (var i = 0; i < tmp.size(); i++)
            tmp.set(i, stacks.get(i).copy());

        var e = new BrewPotionEvent.Pre(tmp);
        EventBus.post(e);
        if (e.isCanceled()) {
            var ch = false;
            for (var i = 0; i < stacks.size(); i++) {
                ch |= ItemStack.matches(tmp.get(i), stacks.get(i));
                stacks.set(i, e.getItem(i));
            }

            if (ch) hc$potionBrewed(stacks);

            return true;
        }

        return false;
    }

    @Unique
    private static void hc$potionBrewed(NonNullList<ItemStack> stacks) {
        var ev = new BrewPotionEvent.Post(stacks);
        EventBus.post(ev);
    }
}
