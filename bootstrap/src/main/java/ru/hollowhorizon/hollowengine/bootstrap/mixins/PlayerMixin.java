package ru.hollowhorizon.hollowengine.bootstrap.mixins;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.api.extensions.PlayerExtension;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements PlayerExtension {
    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow @Nullable public abstract ItemEntity drop(ItemStack droppedItem, boolean dropAround, boolean includeThrowerName);

    @Shadow protected abstract void doCloseContainer();

    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void onInteract(Entity entityToInteractOn, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (level().isClientSide) return;
        if (BootstrapRuntimeManager.bridge().onPlayerInteractEntity((Player) (Object) this, hand, entityToInteractOn)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    //? if fabric {
    @Inject(
        method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onDrop(ItemStack itemStack, boolean includeThrowerName, CallbackInfoReturnable<ItemEntity> cir) {
        ItemEntity dropped = this.drop(itemStack, false, includeThrowerName);
        cir.setReturnValue(BootstrapRuntimeManager.bridge().onPlayerDrop(itemStack, includeThrowerName, dropped, (Player) (Object) this));
    }
    //?}

    @Override
    public void hollowcore$closeContainer() {
        doCloseContainer();
    }
}
