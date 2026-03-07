package ru.hollowhorizon.hollowengine.mixins.components;

import kotlinx.coroutines.CoroutineScopeKt;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
//? if > 1.20.1
/*import net.minecraft.world.level.portal.DimensionTransition;*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import ru.hollowhorizon.hollowengine.common.coroutines.EntityCoroutineScopeProvider;
import ru.hollowhorizon.hollowengine.common.coroutines.EntityScope;
import ru.hollowhorizon.hollowengine.common.coroutines.SerializableCoroutineScope;
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.OwnerScopeRestoredEvent;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.entity.EntityEvent;
import ru.hollowhorizon.hollowengine.common.geary.api.EntityProvider;
import ru.hollowhorizon.hollowengine.common.geary.api.GearyHelper;
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.GearyEntityExtensionsKt;

import java.util.Set;

@Mixin(Entity.class)
public abstract class EntityMixin implements EntityProvider, EntityCoroutineScopeProvider {
    @Unique
    private long hollowengine$entity;
    @Unique
    private SerializableCoroutineScope hollowengine$coroutineScope;
    @Shadow
    private Level level;
    @Shadow
    private int id;

    @Shadow
    public abstract Level level();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(EntityType<?> entityType, Level level, CallbackInfo ci) {
        hollowengine$entity = GearyHelper.create(level(), (Entity) (Object) this);
        hollowengine$coroutineScope = new EntityScope((Entity) (Object) this);
    }

    @Inject(method = "saveWithoutId", at = @At("TAIL"))
    private void serializeExtra(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        var geary = new CompoundTag();
        GearyEntityExtensionsKt.encodeComponentsTo(GearyHelper.getGeary(level()), hollowengine$entity, geary);
        tag.put("geary", geary);
        var scope = new CompoundTag();
        hollowengine$coroutineScope.serialize(scope);
        tag.put("EntityScope", scope);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void deserializeExtra(CompoundTag tag, CallbackInfo ci) {
        GearyEntityExtensionsKt.loadComponentsFrom((Entity) (Object) this, tag.getCompound("geary"));
        hollowengine$coroutineScope.deserialize(tag.getCompound("EntityScope"));
        EventBus.post(new OwnerScopeRestoredEvent((Entity) (Object) this));
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    public void onHurt(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        var event = new EntityEvent.Hurt((Entity) (Object) this, damageSource, amount);
        EventBus.post(event);
        if (event.isCanceled()) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void onTick(CallbackInfo ci) {
        // Geary components are updated by Geary system automatically
    }

    @Inject(method = "changeDimension", at = @At("RETURN"))
    //? if > 1.20.1 {
    /*private void afterWorldChanged(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
    *///?} else {
    private void afterWorldChanged(ServerLevel destination, CallbackInfoReturnable<Entity> cir) {
    //?}
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
            GearyHelper.removeEntity(level(), id);
            CoroutineScopeKt.cancel(hollowengine$coroutineScope, null);
        }
    }

    @Inject(method = "setId", at = @At("HEAD"))
    private void onSetId(int id, CallbackInfo ci) {
        GearyHelper.changeId(level, this.id, id);
    }

    @Override
    public long getHollowengine$entity() {
        return hollowengine$entity;
    }

    @Override
    public SerializableCoroutineScope getHollowengine$coroutineScope() {
        return hollowengine$coroutineScope;
    }
}


