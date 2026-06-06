package ru.hollowhorizon.hollowengine.common.runtime

import ru.hollowhorizon.hollowengine.HollowEngine
import java.lang.reflect.Method

fun loadBootstrapOrRuntimeClass(className: String, fallbackClassLoader: ClassLoader): Class<*>? {
    val localClassLoader = HollowEngine::class.java.classLoader
    val contextClassLoader = Thread.currentThread().contextClassLoader

    return runCatching { Class.forName(className, false, localClassLoader) }.getOrNull()
        ?: runCatching { Class.forName(className, false, contextClassLoader) }.getOrNull()
        ?: runCatching { Class.forName(className, false, fallbackClassLoader) }.getOrNull()
}

fun RuntimeMethodRef.resolve(fallbackClassLoader: ClassLoader): Method? {
    val owner = loadBootstrapOrRuntimeClass(ownerClassName, fallbackClassLoader) ?: return null
    val method = runCatching { owner.methods.first { it.name == methodName } }
    method.exceptionOrNull()?.let { HollowEngine.LOGGER.error("Error while loading $ownerClassName.$methodName(...): ", it) }
    return method.getOrNull()
}
