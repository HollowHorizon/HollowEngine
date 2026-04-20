package ru.hollowhorizon.hollowengine.fabric.bootstap;

import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;
import ru.hollowhorizon.hollowengine.fabric.internal.FabricModList;
import ru.hollowhorizon.hollowengine.fabric.internal.FabricNetworkManager;

public final class HCFabricBootstrap {
    static {
        BootstrapRuntimeManager.bridge().initFakePlayers(new FabricFakePlayerFactory());
        BootstrapRuntimeManager.bridge().initStackHelper(item -> item.getRecipeRemainder());
        BootstrapRuntimeManager.bridge().initNetwork(new FabricNetworkManager());
        BootstrapRuntimeManager.bridge().initModList(new FabricModList());
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
