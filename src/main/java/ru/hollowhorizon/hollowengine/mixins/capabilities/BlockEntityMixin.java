package ru.hollowhorizon.hollowengine.mixins.capabilities;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(BlockEntity.class)
public class BlockEntityMixin implements ICapabilityDispatcher {
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

    @Inject(method = "saveWithoutMetadata", at = @At("TAIL"))
    private void serializeExtra(CallbackInfoReturnable<CompoundTag> cir) {
        ICapabilityDispatcherKt.serializeCapabilities(this, cir.getReturnValue());
    }

    //? if >= 1.21 {
    /*@Inject(method = "loadAdditional", at= @At("TAIL"))
    private void serializeExtra(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
    *///?} else {
    @Inject(method = "load", at = @At("TAIL"))
    private void serializeExtra(CompoundTag tag, CallbackInfo ci) {
    //?}
        ICapabilityDispatcherKt.deserializeCapabilities(this, tag);
    }
}
