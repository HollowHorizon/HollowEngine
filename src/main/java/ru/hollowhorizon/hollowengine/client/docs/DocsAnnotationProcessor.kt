package ru.hollowhorizon.hollowengine.client.docs

import ru.hollowhorizon.hc.common.events.AnnotationProcessorEvent
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import java.lang.invoke.LambdaMetafactory
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Annotation for registering a new page in the documentation. Applies to methods.
 * @param path contains the path to the page separated by '.'
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class DocsPage(val path: String)

@SubscribeEvent
fun onAnnotationProcessor(event: AnnotationProcessorEvent) {
    val handles = MethodHandles.lookup()
    event.getAnnotatedMethods(DocsPage::class.java).forEach {
        val docsPage = it.getAnnotation(DocsPage::class.java)
        val renderer = if (Modifier.isStatic(it.modifiers))
            handles.createStaticPageRenderer(it)
        else {
            val obj = it.declaringClass.kotlin.objectInstance
                ?: throw IllegalArgumentException("${it.declaringClass.simpleName} must be an object!")
            handles.createPageRenderer(it, obj)
        }
        DocsRenderer.PAGES[docsPage.path] = renderer
    }
}

fun interface PageRenderer {
    fun DocsRenderer.render()
}

@Suppress("UNCHECKED_CAST")
fun MethodHandles.Lookup.createStaticPageRenderer(method: Method): PageRenderer {
    try {
        val methodHandle = unreflect(method)
        val callSite = LambdaMetafactory.metafactory(
            this,
            "render",
            MethodType.methodType(PageRenderer::class.java),
            MethodType.methodType(Void.TYPE, method.parameterTypes[0]),
            methodHandle,
            MethodType.methodType(Void.TYPE, method.parameterTypes[0])
        )

        val eventHandle = callSite.target.invoke() as PageRenderer
        return eventHandle
    } catch (t: Throwable) {
        throw IllegalStateException("Error while registering $method", t)
    }
}

@Suppress("UNCHECKED_CAST")
fun MethodHandles.Lookup.createPageRenderer(
    method: Method,
    target: Any,
): PageRenderer {
    try {
        val methodHandle = unreflect(method)
        val callSite = LambdaMetafactory.metafactory(
            this,
            "render",
            MethodType.methodType(PageRenderer::class.java, target.javaClass),
            MethodType.methodType(Void.TYPE, Any::class.java),
            methodHandle,
            MethodType.methodType(Void.TYPE, method.parameterTypes[0])
        )

        val eventHandle = callSite.target.bindTo(target).invokeWithArguments() as PageRenderer
        return eventHandle
    } catch (t: Throwable) {
        throw IllegalStateException("Error while registering $method", t)
    }
}