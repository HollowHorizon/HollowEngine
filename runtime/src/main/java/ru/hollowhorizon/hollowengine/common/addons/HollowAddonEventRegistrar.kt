package ru.hollowhorizon.hollowengine.common.addons

import io.github.classgraph.ClassGraph
import kotlinx.coroutines.CoroutineScope
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.RequireMod
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.createEventListener
import ru.hollowhorizon.hollowengine.common.events.createStaticEventListener
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.utils.ModList
import ru.hollowhorizon.hollowengine.common.utils.isPhysicalClient
import java.io.File
import java.lang.invoke.MethodHandles
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.reflect.KClass

internal object HollowAddonEventRegistrar {
    fun register(
        artifactFile: File,
        classLoader: ClassLoader,
        entrypoint: HollowAddonEntrypoint,
        scope: CoroutineScope,
    ) {
        val annotatedClasses = ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .enableMethodInfo()
            .disableNestedJarScanning()
            .ignoreParentClassLoaders()
            .overrideClassLoaders(classLoader)
            .overrideClasspath(artifactFile)
            .scan()
            .use { scan -> scan.getClassesWithMethodAnnotation(SubscribeEvent::class.java.name).names.toList() }

        annotatedClasses.forEach { className ->
            val type = Class.forName(className, false, classLoader)
            registerType(type, entrypoint, scope)
        }
    }

    internal fun registerType(type: Class<*>, entrypoint: HollowAddonEntrypoint, scope: CoroutineScope) {
        if (!isPhysicalClient && type.isAnnotationPresent(ClientOnly::class.java)) return
        type.declaredMethods
            .filter { method -> method.isAnnotationPresent(SubscribeEvent::class.java) }
            .filter { method -> isPhysicalClient || !method.isAnnotationPresent(ClientOnly::class.java) }
            .filter(::hasRequiredMod)
            .forEach { method -> registerMethod(method, entrypoint, scope) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerMethod(
        method: Method,
        entrypoint: HollowAddonEntrypoint,
        scope: CoroutineScope,
    ) {
        require(Modifier.isPublic(method.modifiers)) {
            "@SubscribeEvent method must be public: $method"
        }
        require(Modifier.isPublic(method.declaringClass.modifiers)) {
            "@SubscribeEvent declaring class must be public: ${method.declaringClass.name}"
        }
        require(method.parameterCount == 1 && Event::class.java.isAssignableFrom(method.parameterTypes.single())) {
            "@SubscribeEvent method must accept exactly one Event: $method"
        }
        require(method.returnType == Void.TYPE) {
            "@SubscribeEvent method must return Unit/void: $method"
        }

        val lookup = MethodHandles.lookup()
        val listener = if (Modifier.isStatic(method.modifiers)) {
            lookup.createStaticEventListener(method)
        } else {
            val target = when (method.declaringClass) {
                entrypoint.javaClass -> entrypoint
                else -> method.declaringClass.kotlin.objectInstance
            } ?: throw IllegalArgumentException(
                "Non-static @SubscribeEvent method must be declared on the addon entrypoint or a Kotlin object: $method",
            )
            lookup.createEventListener(method, target)
        }
        val eventType = method.parameterTypes.single().kotlin as KClass<Event>
        EventHandler.get(eventType).register(scope, listener)
    }

    private fun hasRequiredMod(method: Method): Boolean = method
        .getAnnotation(RequireMod::class.java)
        ?.let { annotation -> ModList.isLoaded(annotation.modId) }
        ?: true
}
