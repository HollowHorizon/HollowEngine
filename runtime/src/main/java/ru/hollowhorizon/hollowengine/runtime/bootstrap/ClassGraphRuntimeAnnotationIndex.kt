package ru.hollowhorizon.hollowengine.runtime.bootstrap

import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hollowengine.common.runtime.RuntimeAnnotationIndex
import ru.hollowhorizon.hollowengine.common.runtime.RuntimeMethodRef
import java.net.URLClassLoader

class ClassGraphRuntimeAnnotationIndex(
    private val scanResult: ScanResult,
) : RuntimeAnnotationIndex, AutoCloseable {
    private val clientOnlyClasses = scanResult
        .getClassesWithAnnotation(CLIENT_ONLY_ANNOTATION)
        .names
        .toSet()

    override fun getAnnotatedClasses(annotationClassName: String, isClient: Boolean): Set<String> {
        return scanResult.getClassesWithAnnotation(annotationClassName)
            .names
            .filterTo(LinkedHashSet()) { isClient || it !in clientOnlyClasses }
    }

    override fun getAnnotatedMethods(annotationClassName: String, isClient: Boolean): Set<RuntimeMethodRef> {
        return scanResult.getClassesWithMethodAnnotation(annotationClassName)
            .flatMapTo(LinkedHashSet()) { classInfo ->
                if (!isClient && classInfo.name in clientOnlyClasses) return@flatMapTo emptyList()

                classInfo.methodInfo
                    .filter { methodInfo -> methodInfo.hasAnnotation(annotationClassName) }
                    .map { methodInfo -> RuntimeMethodRef(classInfo.name, methodInfo.name) }
            }
    }

    override fun getSubTypes(superClassName: String, isClient: Boolean): Set<String> {
        val result = LinkedHashSet<String>()
        result += scanResult.getSubclasses(superClassName)
            .names
            .filter { isClient || it !in clientOnlyClasses }
        result += scanResult.getClassesImplementing(superClassName)
            .names
            .filter { isClient || it !in clientOnlyClasses }
        return result
    }

    companion object {
        private val LOGGER = LogManager.getLogger("HollowEngineRuntime")
        private const val CLIENT_ONLY_ANNOTATION = "ru.hollowhorizon.hollowengine.common.events.ClientOnly"

        fun create(): ClassGraphRuntimeAnnotationIndex {
            val runtimeClassLoader = Thread.currentThread().contextClassLoader
            val runtimeClasspath = (runtimeClassLoader as? URLClassLoader)?.urLs?.toList().orEmpty()
            val scanResult = ClassGraph()
                .enableClassInfo()
                .enableAnnotationInfo()
                .enableMethodInfo()
                .ignoreParentClassLoaders()
                .overrideClassLoaders(runtimeClassLoader)
                .overrideClasspath(runtimeClasspath)
                .acceptPackages("ru.hollowhorizon.hollowengine")
                .scan()

            LOGGER.info(
                "Runtime annotation scan completed: classLoader={}, classpathEntries={}, discoveredClasses={}",
                runtimeClassLoader.javaClass.name,
                runtimeClasspath.size,
                scanResult.allClasses.size,
            )

            return ClassGraphRuntimeAnnotationIndex(scanResult)
        }
    }

    override fun close() {
        scanResult.close()
    }
}
