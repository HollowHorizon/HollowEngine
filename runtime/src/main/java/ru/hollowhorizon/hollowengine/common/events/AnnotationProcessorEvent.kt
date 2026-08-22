package ru.hollowhorizon.hollowengine.common.events

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import java.lang.reflect.Method

class AnnotationProcessorEvent(
    val getAnnotatedClasses: (Class<*>) -> Set<Class<*>>,
    val getSubTypes: (Class<*>) -> Set<Class<*>>,
    val getAnnotatedMethods: (Class<*>) -> Set<Method>,
) : Event {
    companion object : EventHandler<AnnotationProcessorEvent>()

    inline fun <reified T : Annotation> registerClassHandler(noinline task: (Class<*>, T) -> Unit) {
        getAnnotatedClasses(T::class.java).forEach {
            val annotation = it.getAnnotation(T::class.java)
            task(it, annotation)
        }
    }

    inline fun <reified T> registerClassInitializers() {
        getSubTypes(T::class.java).forEach {
            HollowEngine.LOGGER.info("Registering initializer: ${it.simpleName}")
            it.kotlin.objectInstance ?: throw IllegalArgumentException("${T::class.java.simpleName} must be an object!")
        }
    }

    inline fun <reified T : Annotation> registerMethodHandler(noinline task: (Method, T) -> Unit) {
        getAnnotatedMethods(T::class.java).forEach {
            val annotation = it.getAnnotation(T::class.java)
            task(it, annotation)
        }
    }
}