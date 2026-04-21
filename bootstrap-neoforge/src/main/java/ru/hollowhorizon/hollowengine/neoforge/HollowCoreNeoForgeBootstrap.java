package ru.hollowhorizon.hollowengine.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.internal.NeoForgeFakePlayerFactory;
import ru.hollowhorizon.hollowengine.internal.NeoForgeModList;
import ru.hollowhorizon.hollowengine.internal.NeoForgeNetworkManager;
import ru.hollowhorizon.hollowengine.internal.NeoForgeRegistryHolder;

@Mod("hollowengine")
public final class HollowCoreNeoForgeBootstrap {
    public HollowCoreNeoForgeBootstrap(IEventBus modBus) {
        BootstrapRuntimeManager.bridge().initFakePlayers(new NeoForgeFakePlayerFactory());
        BootstrapRuntimeManager.bridge().initStackHelper(item -> item.getCraftingRemainingItem());
        BootstrapRuntimeManager.bridge().initNetwork(new NeoForgeNetworkManager());
        BootstrapRuntimeManager.bridge().initModList(new NeoForgeModList());
        BootstrapRuntimeManager.bridge().initRegistryProvider((location, registry, model, generator, type) ->
                new NeoForgeRegistryHolder<>(modBus, location, registry, model, generator, type));
        BootstrapRuntimeManager.bridge().setProduction(FMLEnvironment.production);

        modBus.addListener(HollowCoreNeoForgeBootstrap::onCommonInitialize);
        modBus.addListener(NeoForgeNetworkManager::onRegisterPackets);
        if (FMLEnvironment.dist.isClient()) {
            modBus.addListener(HollowCoreNeoForgeBootstrap::onClientInitialize);
        }
    }

    public static void onCommonInitialize(FMLCommonSetupEvent event) {
        BootstrapRuntimeManager.bridge().onCommonInitialize();
    }

    @OnlyIn(Dist.CLIENT)
    public static void onClientInitialize(FMLClientSetupEvent event) {
        BootstrapRuntimeManager.bridge().onClientInitialize();
    }
}
