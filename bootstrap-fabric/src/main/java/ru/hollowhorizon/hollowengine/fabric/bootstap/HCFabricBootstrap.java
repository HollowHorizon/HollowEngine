package ru.hollowhorizon.hollowengine.fabric.bootstap;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import ru.hollowhorizon.hollowengine.bootstrap.impl.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.fabric.internal.FabricModList;
import ru.hollowhorizon.hollowengine.fabric.internal.FabricNetworkManager;
import ru.hollowhorizon.hollowengine.fabric.internal.FabricRegistryHolder;

public final class HCFabricBootstrap {
    static {
        BootstrapRuntimeManager.bridge().setProduction(!FabricLoader.getInstance().isDevelopmentEnvironment());
        BootstrapRuntimeManager.bridge().setClient(FabricLoader.getInstance().getEnvironmentType().equals(EnvType.CLIENT));

        BootstrapRuntimeManager.bridge().initFakePlayers(new FabricFakePlayerFactory());
        BootstrapRuntimeManager.bridge().initStackHelper(item -> item.getRecipeRemainder());
        BootstrapRuntimeManager.bridge().initNetwork(new FabricNetworkManager());
        BootstrapRuntimeManager.bridge().initModList(new FabricModList());
        BootstrapRuntimeManager.bridge().initRegistryProvider(FabricRegistryHolder::new);
    }

    public static void onCommonInitialize() {
        FabricCommonEvents.init();
        BootstrapRuntimeManager.bridge().onCommonInitialize();
    }

    public static void onClientInitialize() {
        FabricClientEvents.init();
        BootstrapRuntimeManager.bridge().onClientInitialize();
    }
}
