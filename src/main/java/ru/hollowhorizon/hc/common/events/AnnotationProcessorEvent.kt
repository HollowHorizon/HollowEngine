package ru.hollowhorizon.hc.common.events

import ru.hollowhorizon.hc.HollowCore
import java.lang.reflect.Method

class AnnotationProcessorEvent(
    val getAnnotatedClasses: (Class<*>) -> Set<Class<*>>,
    val getSubTypes: (Class<*>) -> Set<Class<*>>,
    val getAnnotatedMethods: (Class<*>) -> Set<Method>
): Event {
    inline fun <reified T : Annotation> registerClassHandler(noinline task: (Class<*>, T) -> Unit) {
        getAnnotatedClasses(T::class.java).forEach {
            val annotation = it.getAnnotation(T::class.java)
            task(it, annotation)
        }
    }

    inline fun <reified T> registerClassInitializers() {
        getSubTypes(T::class.java).forEach {
            HollowCore.LOGGER.info("Registering initializer: ${it.simpleName}")
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