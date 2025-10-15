package ru.hollowhorizon.hollowengine.mixins.components;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
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
import ru.hollowhorizon.hollowengine.common.components.Component;
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher;
import ru.hollowhorizon.hollowengine.common.components.lifecycle.ComponentSaving;
import ru.hollowhorizon.hollowengine.common.components.lifecycle.ComponentSyncingKt;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.entity.EntityEvent;

import java.util.Map;
import java.util.Set;

@Mixin(Entity.class)
public class EntityMixin implements ComponentDispatcher {
    @Shadow private Level level;
    @Unique
    private final Map<ResourceLocation, Component<?>> hollowCore$components = new Object2ObjectOpenHashMap<>();

    @Inject(method = "saveWithoutId", at = @At("TAIL"))
    private void serializeExtra(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        tag.put(ComponentSaving.COMPONENT_TAG, ComponentSaving.save(this));
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void deserializeExtra(CompoundTag tag, CallbackInfo ci) {
        ComponentSaving.load(this, tag.getCompound(ComponentSaving.COMPONENT_TAG));
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    public void onHurt(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        var event = new EntityEvent.Hurt((Entity) (Object) this, damageSource, amount);
        EventBus.post(event);
        if (event.isCanceled()) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void onTick(CallbackInfo ci) {
        ComponentSyncingKt.onTick(this);
    }

    @Inject(method = "changeDimension", at = @At("RETURN"))
    private void afterWorldChanged(ServerLevel destination, CallbackInfoReturnable<Entity> cir) {
        Entity ret = cir.getReturnValue();

        if (ret != null) {
            EventBus.post(new EntityEvent.ChangeDimension((Entity) (Object) this, ret, level, ret.level()));
        }
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void afterEntityTeleportedToWorld(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relativeMovements, float yRot, float xRot, CallbackInfoReturnable<Boolean> cir, float clampXRot, Entity newEntity) {
        Entity originalEntity = (Entity) (Object) this;
        EventBus.post(new EntityEvent.ChangeDimension(originalEntity, newEntity, originalEntity.level(), level));
    }

    @Override
    public @NotNull Map<@NotNull ResourceLocation, @NotNull Component<?>> getHollowcore$components() {
        return hollowCore$components;
    }
}
