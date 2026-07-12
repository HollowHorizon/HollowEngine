package ru.hollowhorizon.hollowengine.common.addons

import kotlinx.coroutines.CoroutineScope
import org.koin.core.Koin
import org.koin.core.module.Module
import java.io.File
import kotlin.reflect.KClass

data class HollowAddonContext(
    val hostServices: HollowAddonHostServices,
    val descriptor: HollowAddonDescriptor,
    val addonFile: File,
    val classLoader: ClassLoader,
    val koin: Koin,
)

interface HollowAddonEntrypoint {
    val koinModules: List<Module>
        get() = emptyList()

    suspend fun load(context: HollowAddonContext, scope: CoroutineScope)

    suspend fun unload(context: HollowAddonContext) = Unit
}

interface HollowAddonHostServices {
    fun log(message: String)

    fun <T : Any> publishService(type: KClass<T>, instance: T)

    fun <T : Any> unpublishService(type: KClass<T>, instance: T)

    fun <T : Any> findService(type: KClass<T>): T?

    fun <T : Any> findServices(type: KClass<T>): List<T>
}

inline fun <reified T : Any> HollowAddonHostServices.publish(instance: T) = publishService(T::class, instance)

inline fun <reified T : Any> HollowAddonHostServices.unpublish(instance: T) = unpublishService(T::class, instance)

inline fun <reified T : Any> HollowAddonHostServices.find(): T? = findService(T::class)

inline fun <reified T : Any> HollowAddonHostServices.findAll(): List<T> = findServices(T::class)
