package ru.hollowhorizon.hollowengine.fabric.internal;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import ru.hollowhorizon.hollowengine.api.*;
import ru.hollowhorizon.hollowengine.bootstrap.BlockItemCreativeTab;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

import java.util.function.Supplier;

public class FabricRegistryHolder<T> implements RegistryHolder<T> {
    private static final RegistryHelper registryHelper = BootstrapRuntimeManager.bridge().getRegistryHelper();
    private final T result;
    private final Class<T> target;
    private final ResourceLocation location;
    private final AutoModelType autoModel;

    @SuppressWarnings("unchecked")
    public FabricRegistryHolder(ResourceLocation location, Registry<T> registry, AutoModelType autoModel, Supplier<T> supplier, Class<T> target) {
        this.target = target;
        this.location = location;
        this.autoModel = autoModel;

        ResourceKey<? extends Registry<T>> key = registry != null ? registry.key() : registryHelper.registry(target);
        Registry<T> builtRegistry = (Registry<T>) BuiltInRegistries.REGISTRY.get(key.location());
        assert builtRegistry != null;
        this.result = Registry.register(builtRegistry, location, supplier.get());
        handleExtraRegistrations();
    }

    private void handleExtraRegistrations() {
        if (Block.class.isAssignableFrom(target)) {
            if (autoModel != null) registryHelper.addBlockModel(location, autoModel);

            if (BlockItemProperties.class.isAssignableFrom(target)) {
                Block block = (Block) result;
                Item.Properties props = ((BlockItemProperties) block).properties();

                Item item;
                if (block instanceof HasCreativeTab tab) {
                    item = new BlockItemCreativeTab(block, props, tab.tab());
                } else {
                    item = new BlockItem(block, props);
                }

                Registry.register(BuiltInRegistries.ITEM, location, item);

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
        return result;
    }
}
