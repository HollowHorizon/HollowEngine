package ru.hollowhorizon.hc.mixins.capabilities;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hc.api.ICapabilityDispatcher;
import ru.hollowhorizon.hc.api.ICapabilityDispatcherKt;
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance;
import ru.hollowhorizon.hc.common.events.EventBus;
import ru.hollowhorizon.hc.common.events.entity.EntityHurtEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(Entity.class)
public class EntityMixin implements ICapabilityDispatcher {
    @Unique
    private final Map<String, CapabilityInstance> hollowCore$capabilities = new Object2ObjectOpenHashMap<>();

    @NotNull
    @Override
    public Map<String, CapabilityInstance> getCapabilities() {
        return hollowCore$capabilities;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        ICapabilityDispatcherKt.initialize(this);
    }

    @Inject(method = "saveWithoutId", at = @At("TAIL"))
    private void serializeExtra(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        ICapabilityDispatcherKt.serializeCapabilities(this, tag);
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void deserializeExtra(CompoundTag tag, CallbackInfo ci) {
        ICapabilityDispatcherKt.deserializeCapabilities(this, tag);
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    public void onHurt(DamageSource damageSource, float amount, CallbackInfoReturnable<Boolean> cir) {
        var event = new EntityHurtEvent((Entity) (Object) this, damageSource, amount);
        EventBus.post(event);
        if (event.isCanceled()) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void onTick(CallbackInfo ci) {
        ICapabilityDispatcherKt.syncIfNeeded(this);
    }
}
