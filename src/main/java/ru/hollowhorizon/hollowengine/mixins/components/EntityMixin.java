package ru.hollowhorizon.hollowengine.mixins.components;

import com.github.quillraven.fleks.World;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
import ru.hollowhorizon.hollowengine.common.fleks.FleksEntity;
import ru.hollowhorizon.hollowengine.common.fleks.FleksPlatform;
import ru.hollowhorizon.hollowengine.common.fleks.FleksWorld;

import java.util.Set;

@Mixin(Entity.class)
public abstract class EntityMixin implements ComponentDispatcher, FleksEntity {
    @Unique
    private ComponentContainer hollowengine$container;
    @Shadow
    private Level level;
    @Shadow private int id;
    @Unique
    private com.github.quillraven.fleks.Entity hollowengine$fleksEntity;

    @Shadow
    public abstract Level level();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onConstruct(CallbackInfo ci) {
        this.hollowengine$container = new ComponentContainer(this);
        World world = ((FleksWorld) level()).getHollowengine$fleksWorld();
        this.hollowengine$fleksEntity = FleksPlatform.INSTANCE.createEntity(world, (Entity) (Object) this);
    }

    @Inject(method = "saveWithoutId", at = @At("TAIL"))
    private void serializeExtra(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        tag.put(ComponentContainer.COMPONENT_TAG, hollowengine$container.save());
        World world = ((FleksWorld) level()).getHollowengine$fleksWorld();
        if (hollowengine$fleksEntity != null) {
            tag.put("fleks:components", FleksPlatform.INSTANCE.saveEntity(world, hollowengine$fleksEntity));
        }

    }

    @Inject(method = "load", at = @At("TAIL"))
    private void deserializeExtra(CompoundTag tag, CallbackInfo ci) {
        hollowengine$container.load(tag.getCompound(ComponentContainer.COMPONENT_TAG));

        World world = ((FleksWorld) level()).getHollowengine$fleksWorld();
        FleksPlatform.INSTANCE.loadEntity(world, (Entity) (Object) this, hollowengine$fleksEntity, tag.get("fleks:components"));
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

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setRemoved(Lnet/minecraft/world/entity/Entity$RemovalReason;)V"), locals = LocalCapture.CAPTURE_FAILHARD)
    private void afterEntityTeleportedToWorld(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relativeMovements, float yRot, float xRot, CallbackInfoReturnable<Boolean> cir, float clampXRot, Entity newEntity) {
        Entity originalEntity = (Entity) (Object) this;
        EventBus.post(new EntityEvent.ChangeDimension(originalEntity, newEntity, originalEntity.level(), level));
    }

    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void onRemove(Entity.RemovalReason removalReason, CallbackInfo ci) {
        if (!(((Object) this) instanceof Player)) hollowengine$container.detach();

        if (hollowengine$fleksEntity != null) {
            World world = ((FleksWorld) level()).getHollowengine$fleksWorld();
            FleksPlatform.INSTANCE.removeEntity(world, (Entity) (Object) this);
            hollowengine$fleksEntity = null;
        }
    }

    @Inject(method = "setId", at = @At("HEAD"))
    private void onSetId(int id, CallbackInfo ci) {
        World world = ((FleksWorld) level()).getHollowengine$fleksWorld();
        FleksPlatform.INSTANCE.changeId(world, hollowengine$fleksEntity, this.id, id);
    }

    @Override
    public @NotNull com.github.quillraven.fleks.Entity getHollowengine$fleksEntity() {
        return hollowengine$fleksEntity;
    }

    @Override
    public @NotNull ComponentContainer getContainer() {
        return hollowengine$container;
    }
}
