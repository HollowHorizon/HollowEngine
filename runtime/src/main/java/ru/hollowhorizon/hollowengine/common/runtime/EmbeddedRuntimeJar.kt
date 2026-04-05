package ru.hollowhorizon.hollowengine.common.runtime

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object EmbeddedRuntimeJar {
    private const val RUNTIME_JAR_RESOURCE = "META-INF/hollowengine/runtime/HollowEngineRuntime.jar"
    private const val RUNTIME_SHA_RESOURCE = "META-INF/hollowengine/runtime/HollowEngineRuntime.sha256"

    fun extract(anchor: Class<*>, cacheDirectory: File): File? {
        val classLoader = anchor.classLoader
        val checksum = classLoader.getResourceAsStream(RUNTIME_SHA_RESOURCE)
            ?.bufferedReader()
            ?.use { it.readText().trim() }
            ?.takeIf(String::isNotBlank)
            ?: return null

        val runtimeDirectory = cacheDirectory.toPath().resolve("runtime").apply { createDirectories() }
        val target = runtimeDirectory.resolve("HollowEngineRuntime-$checksum.jar")
        if (target.exists()) return target.toFile()

        val temp = runtimeDirectory.resolve("HollowEngineRuntime-$checksum.jar.tmp")
        classLoader.getResourceAsStream(RUNTIME_JAR_RESOURCE)?.use { input ->
            Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING)
        } ?: return null

        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return target.toFile()
    }
}
