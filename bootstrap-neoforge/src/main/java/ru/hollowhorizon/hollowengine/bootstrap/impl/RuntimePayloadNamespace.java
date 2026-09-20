package ru.hollowhorizon.hollowengine.bootstrap.impl;

/** NeoForge uses Mojang names in development and production. */
final class RuntimePayloadNamespace {
    private RuntimePayloadNamespace() {
    }

    static boolean requiresIntermediaryRemap() {
        return false;
    }
}
