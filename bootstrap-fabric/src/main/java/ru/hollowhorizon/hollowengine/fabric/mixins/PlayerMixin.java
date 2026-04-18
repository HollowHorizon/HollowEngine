package ru.hollowhorizon.hollowengine.fabric.mixins;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Shadow
    @Nullable
    public abstract ItemEntity drop(ItemStack droppedItem, boolean dropAround, boolean includeThrowerName);


    @Inject(
            method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onDrop(ItemStack itemStack, boolean includeThrowerName, CallbackInfoReturnable<ItemEntity> cir) {
        ItemEntity dropped = this.drop(itemStack, false, includeThrowerName);
        cir.setReturnValue(BootstrapRuntimeManager.bridge().onPlayerDrop(itemStack, includeThrowerName, dropped, (Player) (Object) this));
    }
}
