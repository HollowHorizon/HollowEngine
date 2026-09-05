package ru.hollowhorizon.hollowengine.bootstrap.impl;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;

/**
 * Rewrites the embedded payload into the namespace the running game uses.
 */
final class RuntimePayloadRemapper {
    private static final String TABLE_RESOURCE = "META-INF/hollowengine/runtime/payload-remap-fabric.tbl.gz";
    private static final String ENTRY_POINT = "ru.hollowhorizon.hollowengine.runtime.remap.PayloadRemapBootstrap";

    private RuntimePayloadRemapper() {
    }

    static File remapIfRequired(Class<?> anchor, File payload, Set<String> parentFirstPackages, Logger logger) throws Exception {
        if (!runsIntermediary()) return payload;

        String name = payload.getName();
        int extension = name.lastIndexOf('.');
        File target = new File(payload.getParentFile(), (extension < 0 ? name : name.substring(0, extension)) + "-intermediary.jar");
        if (target.isFile()) return target;

        File table = extractTable(anchor, payload.getParentFile());
        if (table == null) return payload;

        long started = System.currentTimeMillis();
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        Files.deleteIfExists(temporary.toPath());

        try (ChildFirstUrlClassLoader loader = new ChildFirstUrlClassLoader(new URL[]{payload.toURI().toURL()}, anchor.getClassLoader(), parentFirstPackages, List.of())) {
            Class<?> entryPoint = Class.forName(ENTRY_POINT, true, loader);
            entryPoint.getMethod("remap", String.class, String.class, String.class).invoke(null, payload.getAbsolutePath(), table.getAbsolutePath(), temporary.getAbsolutePath());
        }

        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        logger.info("Remapped runtime payload to intermediary in {} ms", System.currentTimeMillis() - started);
        return target;
    }

    /**
     * True on production Fabric, where the game itself runs in the intermediary namespace.
     */
    private static boolean runsIntermediary() {
        try {
            Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object loader = loaderClass.getMethod("getInstance").invoke(null);
            return !(Boolean) loaderClass.getMethod("isDevelopmentEnvironment").invoke(loader);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            return false;
        }
    }

    private static @Nullable File extractTable(Class<?> anchor, File directory) throws Exception {
        try (InputStream stream = anchor.getClassLoader().getResourceAsStream(TABLE_RESOURCE)) {
            if (stream == null) return null;

            Path target = directory.toPath().resolve("payload-remap.tbl.gz");
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
            return target.toFile();
        }
    }
}
