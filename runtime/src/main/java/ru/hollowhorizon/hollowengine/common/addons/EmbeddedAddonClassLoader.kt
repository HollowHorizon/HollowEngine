package ru.hollowhorizon.hollowengine.common.addons

import java.io.File
import java.net.URLClassLoader

/**
 * Defines the classes of an addon that lives inside a mod jar. The mod loader cannot see the engine's
 * isolated runtime, so a class it defined could not implement [HollowAddonEntrypoint]; the addon
 * package is defined here instead, next to the engine. Every other class, the host mod's own included,
 * still comes from the mod loader, so the addon and the mod share one copy of it.
 */
internal class EmbeddedAddonClassLoader(
    modJar: File,
    private val addonPackage: String,
    parent: ClassLoader,
    private val dependencies: List<ClassLoader>,
) : URLClassLoader(arrayOf(modJar.toURI().toURL()), parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> = synchronized(getClassLoadingLock(name)) {
        findLoadedClass(name)?.let { return@synchronized it }
        if (!name.startsWith(addonPackage)) {
            dependencies.forEach { dependency ->
                try {
                    return@synchronized dependency.loadClass(name)
                } catch (_: ClassNotFoundException) {
                    // Continue through the dependency chain.
                }
            }
            return@synchronized super.loadClass(name, resolve)
        }
        findClass(name).also { loaded -> if (resolve) resolveClass(loaded) }
    }
}
