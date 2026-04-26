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
    return owner.declaredMethods.firstOrNull { it.name == methodName }
}
