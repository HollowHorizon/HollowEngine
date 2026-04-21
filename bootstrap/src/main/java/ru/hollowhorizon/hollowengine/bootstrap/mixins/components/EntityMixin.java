package ru.hollowhorizon.hollowengine.bootstrap.mixins.components;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

import java.util.Set;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow private Level level;
    @Shadow private int id;
    @Shadow public abstract Level level();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(EntityType<?> entityType, Level level, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onEntityCreated((Entity) (Object) this);
    }

    @Inject(method = "saveWithoutId", at = @At("TAIL"))
    private void onSave(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        BootstrapRuntimeManager.bridge().onEntitySaved((Entity) (Object) this, tag);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void onLoad(CompoundTag tag, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onEntityLoaded((Entity) (Object) this, tag);
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void onHurt(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (BootstrapRuntimeManager.bridge().onEntityHurt((Entity) (Object) this, damageSource, amount)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "changeDimension", at = @At("RETURN"))
    private void onChangeDimension(DimensionTransition transition, CallbackInfoReturnable<Entity> cir) {
        BootstrapRuntimeManager.bridge().onEntityChangedDimension((Entity) (Object) this, cir.getReturnValue(), level, transition.newLevel());
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void onSetLevel(Level level, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onEntitySetLevel((Entity) (Object) this, level);
    }

    @Inject(
        method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void onTeleportTo(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relativeMovements, float yRot, float xRot, CallbackInfoReturnable<Boolean> cir, float clampXRot, Entity newEntity) {
        BootstrapRuntimeManager.bridge().onEntityTeleported((Entity) (Object) this, newEntity, ((Entity) (Object) this).level(), level);
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void onRemove(Entity.RemovalReason removalReason, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onEntityRemoved((Entity) (Object) this);
    }

    @Inject(method = "setId", at = @At("HEAD"))
    private void onSetId(int id, CallbackInfo ci) {
        BootstrapRuntimeManager.bridge().onEntitySetId((Entity) (Object) this, id, this.id);
    }
}
