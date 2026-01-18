package ru.hollowhorizon.hollowengine.common.geary.di

import kotlin.reflect.KClass

/**
 * An isolated context for dependency injection.
 *
 * Use [DI] as a global context.
 */
open class DIContext {
    @PublishedApi
    internal val moduleObservers = mutableMapOf<KClass<out Any>, ModuleObserver<out Any>>()

    @PublishedApi
    internal val keyScopes = mutableMapOf<String, ScopedDIContext>()
    internal val kClassScopes = mutableMapOf<KClass<*>, ScopedDIContext>()

    inline fun <reified T> scoped(): ScopedDIContext = scoped(T::class)

    fun scoped(kClass: KClass<*>): ScopedDIContext {
        val simpleName = kClass.simpleName ?: error("Class $kClass has no simple name")
        return kClassScopes.getOrPut(kClass) {
            ScopedDIContext(simpleName = simpleName, byClass = kClass)
        }

    }

    fun scoped(key: String, simpleName: String = key): ScopedDIContext {
        return keyScopes.getOrPut(key) { ScopedDIContext(simpleName = simpleName) }
    }

    /**
     * Gets an observer for a module of type [T].
     *
     * If this module is ever updated, this reference will be updated as well.
     */
    inline fun <reified T : Any> observe(): ModuleObserver<T> = observe(T::class)

    /** Registers a module of type [T]. */
    inline fun <reified T : Any> add(module: T) = add(T::class, module)

    inline fun <reified T : Any> addByDelegate(noinline delegate: () -> T) = addByDelegate(T::class, delegate)

    inline fun <reified T : Any> remove() = remove(T::class)

    /** Gets a module by type directly */
    inline fun <reified T : Any> get(): T = get(T::class)

    /** Gets a module by type directly */
    inline fun <reified T : Any> getOrNull(): T? = getOrNull(T::class)

    fun <T : Any> observe(type: KClass<T>): ModuleObserver<T> = getOrPutModuleObserver(type)

    fun <T : Any> addByClass(module: T) {
        add(module::class, module)
    }

    fun <T : Any> add(type: KClass<out T>, module: T) {
        getOrPutModuleObserver(type).apply {
            this.module = module
            delegating = false
        }
    }

    fun <T : Any> addByDelegate(type: KClass<out T>, delegate: () -> T) {
        getOrPutModuleObserver(type).apply {
            this.delegate = delegate
            delegating = true
        }
    }

    fun <T : Any> remove(type: KClass<T>) {
        moduleObservers[type]?.module = null
    }

    fun <T : Any> get(type: KClass<T>): T = getOrNull(type) ?: error("Module ${type.simpleName} not registered")

    @Suppress("UNCHECKED_CAST") // Logic ensures safety
    fun <T : Any> getOrNull(type: KClass<T>): T? = getOrPutModuleObserver(type).getOrNull()

    fun clear() {
        moduleObservers.forEach { it.value.module = null }
        (keyScopes + kClassScopes).forEach { it.value.clear() }
    }

    @Suppress("UNCHECKED_CAST") // Logic ensures safety
    private fun <T : Any> getOrPutModuleObserver(type: KClass<out T>): ModuleObserver<T> {
        return moduleObservers.getOrPut(type) {
            ModuleObserver<T>(type.simpleName ?: "Unknown Class")
        } as ModuleObserver<T>
    }
}