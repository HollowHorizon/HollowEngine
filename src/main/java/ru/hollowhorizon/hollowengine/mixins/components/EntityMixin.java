package ru.hollowhorizon.hollowengine.mixins.components;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import ru.hollowhorizon.hollowengine.common.components.ComponentContainer;
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.entity.EntityEvent;
import ru.hollowhorizon.hollowengine.common.geary.api.EntityProvider;
import ru.hollowhorizon.hollowengine.common.geary.api.GearyHelper;
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.GearyEntityExtensionsKt;

import java.util.Set;

@Mixin(Entity.class)
public abstract class EntityMixin implements ComponentDispatcher, EntityProvider {
    @Unique
    private ComponentContainer hollowengine$container = new ComponentContainer(this);
    @Unique
    private long hollowengine$entity;
    @Shadow
    private Level level;
    @Shadow
    private int id;

    @Shadow
    public abstract Level level();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(EntityType<?> entityType, Level level, CallbackInfo ci) {
        hollowengine$container = new ComponentContainer(this);
        hollowengine$entity = GearyHelper.create(level(), (Entity) (Object) this);
    }

    @Inject(method = "saveWithoutId", at = @At("TAIL"))
    private void serializeExtra(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        tag.put(ComponentContainer.COMPONENT_TAG, hollowengine$container.save());
        var geary = new CompoundTag();
        GearyEntityExtensionsKt.encodeComponentsTo(GearyHelper.getGeary(level()), hollowengine$entity, geary);
        tag.put("geary", geary);

    }

    @Inject(method = "load", at = @At("TAIL"))
    private void deserializeExtra(CompoundTag tag, CallbackInfo ci) {
        hollowengine$container.load(tag.getCompound(ComponentContainer.COMPONENT_TAG));
        GearyEntityExtensionsKt.loadComponentsFrom(hollowengine$entity, (Entity) (Object) this, tag.getCompound("geary"));
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    public void onHurt(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        var event = new EntityEvent.Hurt((Entity) (Object) this, damageSource, amount);
        EventBus.post(event);
        if (event.isCanceled()) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void onTick(CallbackInfo ci) {
        hollowengine$container.update();
    }

    @Inject(method = "changeDimension", at = @At("RETURN"))
    private void afterWorldChanged(ServerLevel destination, CallbackInfoReturnable<Entity> cir) {
        Entity ret = cir.getReturnValue();

        if (ret != null) {
            EventBus.post(new EntityEvent.ChangeDimension((Entity) (Object) this, ret, level, ret.level()));
        }
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void onSetLevel(Level level, CallbackInfo ci) {
        // Обновляем сущность под новый мир
        hollowengine$entity = GearyHelper.move(level(), level, hollowengine$entity, (Entity) (Object) this);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void afterEntityTeleportedToWorld(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relativeMovements, float yRot, float xRot, CallbackInfoReturnable<Boolean> cir, float clampXRot, Entity newEntity) {
        Entity originalEntity = (Entity) (Object) this;
        EventBus.post(new EntityEvent.ChangeDimension(originalEntity, newEntity, originalEntity.level(), level));
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void onRemove(Entity.RemovalReason removalReason, CallbackInfo ci) {
        if (!(((Object) this) instanceof Player)) {
            hollowengine$container.detach();
            GearyHelper.removeEntity(level(), id);
        }
    }

    @Inject(method = "setId", at = @At("HEAD"))
    private void onSetId(int id, CallbackInfo ci) {
        GearyHelper.changeId(level, this.id, id);
    }


    @Override
    public @NotNull ComponentContainer getContainer() {
        return hollowengine$container;
    }

    @Override
    public long getHollowengine$entity() {
        return hollowengine$entity;
    }
}
