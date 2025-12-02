package ru.hollowhorizon.hollowengine.mixins;

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
import ru.hollowhorizon.hollowengine.common.utils.JavaHacks;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerInteractEvent;

//? if fabric {
import ru.hollowhorizon.hollowengine.common.events.entity.ItemEntityEvent;
import java.util.Objects;
//?}

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity {
    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) { super(entityType, level); }

    @Shadow @Nullable public abstract ItemEntity drop(ItemStack droppedItem, boolean dropAround, boolean includeThrowerName);

    @Inject(method = "interactOn", at = @At("HEAD"), cancellable = true)
    private void onInteract(Entity entityToInteractOn, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        // На клиенте это событие уже было (MultiPlayerGameMode вызывает этот метод с клиента, а на сервере он должен вызываться тут)
        if (level().isClientSide) return;
        var event = new PlayerInteractEvent.EntityInteract(JavaHacks.forceCast(this), hand, entityToInteractOn);
        EventBus.post(event);
        if (event.isCanceled()) cir.setReturnValue(InteractionResult.PASS);
    }

    //? if fabric {
    @Inject(
            method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At("HEAD"),
            cancellable = true
    )
    public void drop(ItemStack itemStack, boolean includeThrowerName, CallbackInfoReturnable<ItemEntity> cir) {
        var r = this.drop(itemStack, false, includeThrowerName);
        if (r == null) {
            cir.setReturnValue(null);
            return;
        }

        var e = new ItemEntityEvent.Toss(Objects.requireNonNull(r), JavaHacks.forceCast(this));
        EventBus.post(e);
        if (e.isCanceled()) cir.setReturnValue(null);

        if (!level().isClientSide)
            this.getCommandSenderWorld().addFreshEntity(e.getEntity());

        cir.setReturnValue(e.getEntity());
    }
    //?}
}
