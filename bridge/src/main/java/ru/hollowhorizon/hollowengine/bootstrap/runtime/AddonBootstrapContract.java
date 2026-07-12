package ru.hollowhorizon.hollowengine.bootstrap.runtime;

import java.util.List;

public final class AddonBootstrapContract {
    public static final String BOOTSTRAP_LIBRARY_PATH = "hollowengine-addon-bootstrap/";
    public static final String REGULAR_LIBRARY_PATH = "hollowengine-addon-libs/";
    public static final String LOADED_ADDON_FINGERPRINTS_PROPERTY = "hollowengine.addons.bootstrap.loaded";
    public static final String REJECTED_ADDON_FINGERPRINTS_PROPERTY = "hollowengine.addons.bootstrap.rejected";
    public static final List<String> HOST_NATIVE_LIBRARY_PREFIXES = List.of(
            "lwjgl", "jemalloc", "glfw", "openal", "opengl", "stb", "tinyfd", "shaderc", "vulkan",
            "jinput", "jna-", "jna-platform-", "netty-", "netty-tcnative", "oshi-core"
    );

    private AddonBootstrapContract() {
    }
}
