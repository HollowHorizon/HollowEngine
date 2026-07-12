package ru.hollowhorizon.hollowengine.common.addons

import java.net.URL
import java.net.URLClassLoader

internal class HollowAddonClassLoader(
    urls: Array<URL>,
    parent: ClassLoader,
    private val dependencies: List<ClassLoader>,
) : URLClassLoader(urls, parent) {
    private val parentFirstPackages = listOf(
        "java.",
        "javax.",
        "jdk.",
        "sun.",
        "com.sun.",
        "ru.hollowhorizon.hollowengine.",
        "net.minecraft.",
        "net.fabricmc.",
        "net.neoforged.",
        "net.minecraftforge.",
        "com.mojang.",
        "kotlin.",
        "kotlinx.coroutines.",
        "org.koin.",
        "org.lwjgl.",
        "org.lwjglx.",
        "org.joml.",
        "org.bytedeco.",
        "com.sun.jna.",
        "io.netty.",
        "oshi.",
        "org.slf4j.",
        "org.apache.logging.",
    )

    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
        findLoadedClass(name)?.let { return@synchronized it }
        if (parentFirstPackages.any(name::startsWith)) {
            try {
                return@synchronized parent.loadClass(name)
            } catch (_: ClassNotFoundException) {
                // Addon-owned classes may share the engine's package prefix.
            }
        }
        dependencies.forEach { dependency ->
            try {
                return@synchronized dependency.loadClass(name)
            } catch (_: ClassNotFoundException) {
                // Continue through the dependency chain.
            }
        }
        val loaded = try {
            findClass(name).also { loaded ->
                if (resolve) resolveClass(loaded)
            }
        } catch (_: ClassNotFoundException) {
            super.loadClass(name, resolve)
        }
        loaded
    }
}
