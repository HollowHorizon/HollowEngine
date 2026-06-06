package ru.hollowhorizon.hollowengine.runtime.bootstrap

import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import org.apache.logging.log4j.LogManager
import ru.hollowhorizon.hollowengine.common.events.RequireMod
import ru.hollowhorizon.hollowengine.common.runtime.RuntimeAnnotationIndex
import ru.hollowhorizon.hollowengine.common.runtime.RuntimeMethodRef
import ru.hollowhorizon.hollowengine.common.utils.ModList
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
                    .asSequence()
                    .filter { methodInfo -> methodInfo.hasAnnotation(annotationClassName) }
                    .filter { methodInfo ->
                        if (!methodInfo.hasAnnotation(RequireMod::class.java)) return@filter true
                        val mod =
                            methodInfo.getAnnotationInfo(RequireMod::class.java).parameterValues[0].value as String
                        ModList.isLoaded(mod)
                    }
                    .mapTo(mutableListOf()) { methodInfo -> RuntimeMethodRef(classInfo.name, methodInfo.name) }
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
