package ru.hollowhorizon.hollowengine.mixins.registry;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import com.mojang.serialization.Lifecycle;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.hollowhorizon.hollowengine.common.registry.extend.HollowDynamicRegistry;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Mixin(MappedRegistry.class)
public abstract class MappedRegistryDynamicMixin<T> implements HollowDynamicRegistry {
    @Shadow
    private boolean frozen;

    @Shadow
    @Final
    private ObjectList<Holder.Reference<T>> byId;

    @Shadow
    @Final
    private Object2IntMap<T> toId;

    @Shadow
    @Final
    private Map<ResourceLocation, Holder.Reference<T>> byLocation;

    @Shadow
    @Final
    private Map<ResourceKey<T>, Holder.Reference<T>> byKey;

    @Shadow
    @Final
    private Map<T, Holder.Reference<T>> byValue;

    @Shadow
    @Final
    private Map<T, Lifecycle> lifecycles;

    @Shadow
    private Lifecycle registryLifecycle;

    @Shadow
    private int nextId;

    @Shadow
    @Nullable
    private List<Holder.Reference<T>> holdersInOrder;

    @Shadow
    @Nullable
    private Map<T, Holder.Reference<T>> unregisteredIntrusiveHolders;

    @Shadow
    public abstract HolderOwner<T> holderOwner();

    @Shadow
    public abstract HolderLookup.RegistryLookup<T> asLookup();

    @Unique
    private final List<ResourceKey<T>> hollow$dynamicKeys = new ArrayList<>();

    @Unique
    private final List<T> hollow$dynamicValues = new ArrayList<>();

    @Unique
    private final Map<T, Holder.Reference<T>> hollow$dynamicIntrusiveHolders = new IdentityHashMap<>();

    @Inject(
        method = "createIntrusiveHolder",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hollow$createIntrusiveHolder(T value, CallbackInfoReturnable<Holder.Reference<T>> cir) {
        if (!this.frozen) return;
        Holder.Reference<T> reference =
            this.hollow$dynamicIntrusiveHolders.computeIfAbsent(value, v -> Holder.Reference.createIntrusive(this.asLookup(), v));
        cir.setReturnValue(reference);
    }

    @Inject(
        method = "registerMapping",
        at = @At("HEAD"),
        cancellable = true
    )
    private void hollow$registerDynamic(int id, ResourceKey<T> key, T value, Lifecycle lifecycle, CallbackInfoReturnable<Holder.Reference<T>> cir) {
        if (!this.frozen) return;

        Objects.requireNonNull(key);
        Objects.requireNonNull(value);

        if (this.byLocation.containsKey(key.location())) {
            Util.pauseInIde(new IllegalStateException("Adding duplicate key '" + key + "' to registry"));
        }
        if (this.byValue.containsKey(value)) {
            Util.pauseInIde(new IllegalStateException("Adding duplicate value '" + value + "' to registry"));
        }

        Holder.Reference<T> reference;
        if (this.unregisteredIntrusiveHolders != null) {
            reference = this.unregisteredIntrusiveHolders.remove(value);
            if (reference == null) {
                throw new AssertionError("Missing intrusive holder for " + key + ":" + value);
            }
            ((HolderReferenceAccessor<T>) reference).hollow$bindKey(key);
        } else {
            reference = this.hollow$dynamicIntrusiveHolders.remove(value);
            if (reference != null) {
                ((HolderReferenceAccessor<T>) reference).hollow$bindKey(key);
            } else {
                reference = Holder.Reference.createStandAlone(this.holderOwner(), key);
            }
        }

        ((HolderReferenceAccessor<T>) reference).hollow$bindValue(value);

        this.byKey.put(key, reference);
        this.byLocation.put(key.location(), reference);
        this.byValue.put(value, reference);
        this.byId.size(Math.max(this.byId.size(), id + 1));
        this.byId.set(id, reference);
        this.toId.put(value, id);
        if (this.nextId <= id) {
            this.nextId = id + 1;
        }
        this.lifecycles.put(value, lifecycle);
        this.registryLifecycle = this.registryLifecycle.add(lifecycle);
        this.holdersInOrder = null;

        this.hollow$dynamicKeys.add(key);
        this.hollow$dynamicValues.add(value);

        cir.setReturnValue(reference);
    }

    @Override
    public void hollow$clearDynamic() {
        for (T value : this.hollow$dynamicValues) {
            int id = this.toId.getInt(value);
            if (id >= 0 && id < this.byId.size()) {
                this.byId.set(id, null);
            }
            this.toId.removeInt(value);
            this.byValue.remove(value);
            this.lifecycles.remove(value);
        }

        for (ResourceKey<T> key : this.hollow$dynamicKeys) {
            this.byKey.remove(key);
            this.byLocation.remove(key.location());
        }

        this.hollow$dynamicKeys.clear();
        this.hollow$dynamicValues.clear();
        this.hollow$dynamicIntrusiveHolders.clear();
        this.holdersInOrder = null;
    }

    @Override
    public ResourceKey<?> hollow$getKey(ResourceLocation id) {
        Holder.Reference<T> reference = this.byLocation.get(id);
        return reference == null ? null : reference.key();
    }

    @Override
    public boolean hollow$isPresent(ResourceLocation id) {
        return this.byLocation.containsKey(id);
    }
}
