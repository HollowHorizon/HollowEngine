package ru.hollowhorizon.hollowengine.common.runtime

import java.net.URL
import java.net.URLClassLoader
import java.util.Collections
import java.util.Enumeration
import java.util.LinkedHashSet

class ChildFirstUrlClassLoader(
    urls: Array<URL>,
    parent: ClassLoader,
    private val parentFirstPackages: Set<String>,
) : URLClassLoader(urls, parent) {

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            findLoadedClass(name)?.let { loaded ->
                if (resolve) resolveClass(loaded)
                return loaded
            }

            if (parentFirstPackages.any(name::startsWith)) {
                return super.loadClass(name, resolve)
            }

            try {
                val childClass = findClass(name)
                if (resolve) resolveClass(childClass)
                return childClass
            } catch (_: ClassNotFoundException) {
                return super.loadClass(name, resolve)
            }
        }
    }

    override fun getResource(name: String): URL? {
        val childResource = findResource(name)
        return childResource ?: super.getResource(name)
    }

    override fun getResources(name: String): Enumeration<URL> {
        val resources = LinkedHashSet<URL>()
        findResources(name).toList(resources)
        super.getResources(name).toList(resources)
        return Collections.enumeration(resources)
    }

    private fun Enumeration<URL>.toList(result: MutableSet<URL>) {
        while (hasMoreElements()) result += nextElement()
    }
}
