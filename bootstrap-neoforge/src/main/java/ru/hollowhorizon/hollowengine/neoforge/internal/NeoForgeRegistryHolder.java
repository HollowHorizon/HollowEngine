package ru.hollowhorizon.hollowengine.neoforge.internal;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import ru.hollowhorizon.hollowengine.api.*;
import ru.hollowhorizon.hollowengine.bootstrap.BlockItemCreativeTab;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;

import java.util.function.Supplier;

public class NeoForgeRegistryHolder<T> implements RegistryHolder<T> {
    private static final RegistryHelper registryHelper = BootstrapRuntimeManager.bridge().getRegistryHelper();
    private final IEventBus modBus;
    private final ResourceLocation location;
    private final AutoModelType autoModel;
    private final Class<T> target;
    private final DeferredHolder<T, T> result;

    public NeoForgeRegistryHolder(IEventBus modBus, ResourceLocation location, Registry<T> registry, AutoModelType autoModel, Supplier<T> supplier, Class<T> target) {
        this.modBus = modBus;
        this.location = location;
        this.autoModel = autoModel;
        this.target = target;
        ResourceKey<? extends Registry<T>> key = registry != null ? registry.key() : registryHelper.registry(target);
        DeferredRegister<T> deferredRegister = DeferredRegister.create(key, location.getNamespace());
        this.result = deferredRegister.register(location.getPath(), supplier);

        handleExtraRegistrations();
        deferredRegister.register(modBus);
    }

    private void handleExtraRegistrations() {
        if (Block.class.isAssignableFrom(target)) {
            if (autoModel != null) registryHelper.addBlockModel(location, autoModel);

            if (BlockItemProperties.class.isAssignableFrom(target)) {
                DeferredRegister<Item> items = DeferredRegister.create(BuiltInRegistries.ITEM, location.getNamespace());
                items.register(location.getPath(), () -> {
                    Block block = (Block) result.get();
                    Item.Properties props = ((BlockItemProperties) block).properties();

                    if (block instanceof HasCreativeTab tab) {
                        return new BlockItemCreativeTab(block, props, tab.tab());
                    } else {
                        return new BlockItem(block, props);
                    }
                });
                items.register(modBus);

                if (autoModel != null) {
                    if (autoModel == AutoModelType.CUBE_ALL) {
                        registryHelper.addItemModel(location, AutoModelType.custom(location.getNamespace() + ":block/" + location.getPath()));
                    } else {
                        registryHelper.addItemModel(location, AutoModelType.custom("builtin/entity"));
                    }
                }
            }
        } else if (Item.class.isAssignableFrom(target)) {
            if (autoModel != null) registryHelper.addItemModel(location, autoModel);
        }
    }

    @Override
    public T get() {
        return result.get();
    }
}
