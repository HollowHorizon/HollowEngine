package ru.hollowhorizon.hollowengine.bootstrap.impl;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class EmbeddedRuntimeJar {
    private static final String RUNTIME_JAR = "META-INF/hollowengine/runtime/HollowEngineRuntime.jar";
    private static final String RUNTIME_SHA = "META-INF/hollowengine/runtime/HollowEngineRuntime.sha256";

    private EmbeddedRuntimeJar() {
    }

    static @Nullable File extract(Class<?> anchor, File cacheDirectory) throws IOException {
        ClassLoader classLoader = anchor.getClassLoader();
        String checksum;
        try (InputStream shaStream = classLoader.getResourceAsStream(RUNTIME_SHA)) {
            if (shaStream == null) return null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(shaStream, StandardCharsets.UTF_8))) {
                checksum = reader.readLine();
            }
        }

        if (checksum == null || checksum.isBlank()) return null;

        Path runtimeDir = cacheDirectory.toPath().resolve("runtime");
        Files.createDirectories(runtimeDir);
        Path target = runtimeDir.resolve("HollowEngineRuntime-" + checksum + ".jar");
        if (Files.exists(target)) return target.toFile();

        Path temp = runtimeDir.resolve("HollowEngineRuntime-" + checksum + ".jar.tmp");
        try (InputStream jarStream = classLoader.getResourceAsStream(RUNTIME_JAR)) {
            if (jarStream == null) return null;
            Files.copy(jarStream, temp, StandardCopyOption.REPLACE_EXISTING);
        }

        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target.toFile();
    }
}
