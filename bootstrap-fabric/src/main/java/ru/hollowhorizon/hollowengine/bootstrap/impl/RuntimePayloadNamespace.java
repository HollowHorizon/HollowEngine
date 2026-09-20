package ru.hollowhorizon.hollowengine.bootstrap.impl;

import net.fabricmc.loader.api.FabricLoader;

/** Selects the namespace of the game classes visible to the isolated runtime on Fabric. */
final class RuntimePayloadNamespace {
    private RuntimePayloadNamespace() {
    }

    static boolean requiresIntermediaryRemap() {
        return !FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
