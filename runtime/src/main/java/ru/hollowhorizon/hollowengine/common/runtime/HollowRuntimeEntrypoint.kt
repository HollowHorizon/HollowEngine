package ru.hollowhorizon.hollowengine.common.runtime

import org.apache.logging.log4j.Logger
import java.io.File

interface HollowRuntimeEntrypoint : AutoCloseable {
    val annotationIndex: RuntimeAnnotationIndex

    fun initialize(context: HollowRuntimeBootstrapContext)

    override fun close() = Unit
}

data class HollowRuntimeBootstrapContext(
    val modId: String,
    val gameDirectory: File,
    val cacheDirectory: File,
    val isProduction: Boolean,
    val logger: Logger,
)

interface RuntimeAnnotationIndex {
    fun getAnnotatedClasses(annotationClassName: String, isClient: Boolean): Set<String>
    fun getAnnotatedMethods(annotationClassName: String, isClient: Boolean): Set<RuntimeMethodRef>
    fun getSubTypes(superClassName: String, isClient: Boolean): Set<String>
}

data class RuntimeMethodRef(
    val ownerClassName: String,
    val methodName: String,
)

object EmptyRuntimeAnnotationIndex : RuntimeAnnotationIndex {
    override fun getAnnotatedClasses(annotationClassName: String, isClient: Boolean) = emptySet<String>()

    override fun getAnnotatedMethods(annotationClassName: String, isClient: Boolean) = emptySet<RuntimeMethodRef>()

    override fun getSubTypes(superClassName: String, isClient: Boolean) = emptySet<String>()
}
