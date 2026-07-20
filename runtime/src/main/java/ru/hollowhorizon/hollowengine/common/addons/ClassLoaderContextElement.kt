package ru.hollowhorizon.hollowengine.common.addons

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal class ClassLoaderContextElement(
    private val classLoader: ClassLoader,
) : ThreadContextElement<ClassLoader?>, AbstractCoroutineContextElement(Key) {
    override fun updateThreadContext(context: CoroutineContext): ClassLoader? {
        val thread = Thread.currentThread()
        return thread.contextClassLoader.also { thread.contextClassLoader = classLoader }
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: ClassLoader?) {
        Thread.currentThread().contextClassLoader = oldState
    }

    private companion object Key : CoroutineContext.Key<ClassLoaderContextElement>
}
