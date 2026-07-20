package ru.hollowhorizon.hollowengine.bootstrap.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.hollowhorizon.hollowengine.bootstrap.impl.transform.RuntimeClassTransformers;
import ru.hollowhorizon.hollowengine.bootstrap.runtime.RuntimeBridge;

import java.io.File;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class BootstrapRuntimeManager {
    private static final Logger LOGGER = LogManager.getLogger("HollowEngineBootstrap");
    private static final String BRIDGE_CLASS = "ru.hollowhorizon.hollowengine.bootstrap.RuntimeBridgeEntrypoint";
    private static final Set<String> PARENT_FIRST_PACKAGES = Set.of(
            "java.",
            "javax.",
            "jdk.",
            "sun.",
            "com.sun.",
            "net.minecraft.",
            "net.minecraftforge.",
            "net.neoforged.",
            "cpw.mods.",
            "org.spongepowered.",
            "org.apache.logging.log4j.",
            "ru.hollowhorizon.hollowengine.bootstrap.runtime.",
            "ru.hollowhorizon.hollowengine.bridge."
    );

    private static ChildFirstUrlClassLoader classLoader;
    private static RuntimeBridge bridge;

    private BootstrapRuntimeManager() {
    }

    public static synchronized void initialize() {
        if (bridge != null) return;

        try {
            File runtimeJar = resolveRuntimeJar();
            File hollowEngineDirectory = new File("hollowengine");
            AddonBootstrapLibraries.Result addonLibraries = AddonBootstrapLibraries.discover(
                    new File(hollowEngineDirectory, "addons"),
                    new File(hollowEngineDirectory, ".cache"),
                    LOGGER
            );
            List<URL> runtimeClasspath = new ArrayList<>();
            runtimeClasspath.add(runtimeJar.toURI().toURL());
            runtimeClasspath.addAll(addonLibraries.urls());

            ChildFirstUrlClassLoader loader = new ChildFirstUrlClassLoader(
                    runtimeClasspath.toArray(URL[]::new),
                    RuntimeBridge.class.getClassLoader(),
                    PARENT_FIRST_PACKAGES,
                    RuntimeClassTransformers.createDefault()
            );

            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            try {
                thread.setContextClassLoader(loader);
                Class<?> bridgeClass = Class.forName(BRIDGE_CLASS, true, loader);
                Constructor<?> constructor = bridgeClass.getDeclaredConstructor();
                Object instance = constructor.newInstance();
                bridge = (RuntimeBridge) instance;
                classLoader = loader;
                LOGGER.info("Loaded isolated runtime from {}", runtimeJar.getName());
            } finally {
                thread.setContextClassLoader(previous);
            }
        } catch (Exception exception) {
            throw new RuntimeException("Failed to initialize isolated HollowEngine runtime", exception);
        }
    }

    private static File resolveRuntimeJar() throws Exception {
        String externalRuntimePath = System.getProperty("hollowengine.runtimeJar");
        if (externalRuntimePath != null && !externalRuntimePath.isBlank()) {
            File runtimeJar = new File(externalRuntimePath);
            if (runtimeJar.isFile()) {
                LOGGER.info("Loaded isolated runtime from external path {}", runtimeJar.getAbsolutePath());
                return runtimeJar;
            }

            throw new IllegalStateException("Runtime jar configured via hollowengine.runtimeJar was not found: " + runtimeJar.getAbsolutePath());
        }

        File cacheDir = new File("hollowengine/.cache");
        File runtimeJar = EmbeddedRuntimeJar.extract(BootstrapRuntimeManager.class, cacheDir);
        if (runtimeJar != null) {
            return runtimeJar;
        }

        throw new IllegalStateException("Embedded runtime jar was not found in bootstrap resources");
    }

    public static RuntimeBridge bridge() {
        initialize();
        return bridge;
    }

    public static synchronized void close() {
        try {
            if (bridge != null) bridge.close();
        } catch (Exception exception) {
            LOGGER.error("Failed to close runtime bridge", exception);
        }

        try {
            if (classLoader != null) classLoader.close();
        } catch (Exception exception) {
            LOGGER.error("Failed to close runtime classloader", exception);
        }

        bridge = null;
        classLoader = null;
    }
}
