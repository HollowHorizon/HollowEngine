package ru.hollowhorizon.hollowengine.bootstrap.fabric;

import ru.hollowhorizon.hollowengine.bootstrap.runtime.BootstrapRuntimeManager;

public final class HCFabricBootstrap {
    private HCFabricBootstrap() {
    }

    public static void onCommonInitialize() {
        BootstrapRuntimeManager.bridge().onCommonInitialize();
    }

    public static void onClientInitialize() {
        BootstrapRuntimeManager.bridge().onClientInitialize();
    }
}
