package ru.hollowhorizon.hollowengine.mixins.capabilities;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.api.ICapabilityDispatcher;
import ru.hollowhorizon.hollowengine.api.ICapabilityDispatcherKt;
import ru.hollowhorizon.hollowengine.common.capabilities.CapabilityInstance;
import ru.hollowhorizon.hollowengine.common.components.Component;
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher;
import ru.hollowhorizon.hollowengine.common.components.lifecycle.ComponentSavingKt;
import ru.hollowhorizon.hollowengine.common.components.lifecycle.ComponentSyncingKt;
import ru.hollowhorizon.hollowengine.common.events.EventBus;
import ru.hollowhorizon.hollowengine.common.events.entity.EntityHurtEvent;

import java.util.Map;

@Mixin(Entity.class)
public class EntityMixin implements ICapabilityDispatcher, ComponentDispatcher {
    @Unique
    private final Map<String, CapabilityInstance> hollowCore$capabilities = new Object2ObjectOpenHashMap<>();
    @Unique
    private final Map<ResourceLocation, Component<?>> hollowCore$components = new Object2ObjectOpenHashMap<>();


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
        tag.put(ComponentSavingKt.COMPONENT_TAG, ComponentSavingKt.save(this));
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void deserializeExtra(CompoundTag tag, CallbackInfo ci) {
        ICapabilityDispatcherKt.deserializeCapabilities(this, tag);
        ComponentSavingKt.load(this, tag.getCompound(ComponentSavingKt.COMPONENT_TAG));
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
        ComponentSyncingKt.onTick(this);
    }

    @Override
    public @NotNull Map<@NotNull ResourceLocation, @NotNull Component<?>> getHollowcore$components() {
        return hollowCore$components;
    }
}
