package ru.hollowhorizon.hollowengine.bootstrap.impl;

import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

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

        cleanDirectory(cacheDirectory);

        try (InputStream jarStream = classLoader.getResourceAsStream(RUNTIME_JAR)) {
            if (jarStream == null) return null;
            Files.createDirectory(target.getParent());
            Files.createFile(target);
            Files.copy(jarStream, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return target.toFile();
    }

    private static void cleanDirectory(File file) throws IOException {
        var path = file.toPath();
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(path))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }
}
