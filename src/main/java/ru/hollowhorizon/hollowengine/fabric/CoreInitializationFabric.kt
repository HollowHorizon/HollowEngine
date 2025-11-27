//? if fabric {
package ru.hollowhorizon.hollowengine.fabric

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.github.classgraph.MethodInfo
import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.registry.HollowModProcessor
import ru.hollowhorizon.hollowengine.common.registry.getAnnotatedClasses
import ru.hollowhorizon.hollowengine.common.registry.getAnnotatedMethods
import ru.hollowhorizon.hollowengine.common.registry.getSubTypes
import sun.misc.Unsafe
import java.lang.reflect.Method

private val unsafe by lazy {
    val theUnsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
    theUnsafe.isAccessible = true
    theUnsafe[null] as Unsafe
}

@Suppress("UNCHECKED_CAST")
fun <T> findField(lookup: Any, name: String): T {
    val lookupClass = lookup::class.java
    val field = lookupClass.getDeclaredField(name) // Why did you have to make it private?
    val offset = unsafe.objectFieldOffset(field)
    return unsafe.getObject(lookup, offset) as T
}

object CoreInitializationFabric {
    init {
        val graph = ClassGraph()
            .enableAllInfo()
            .scan()

        val isClient = FabricLoader.getInstance().environmentType == EnvType.CLIENT

        getSubTypes =
            {
                graph.getSubclasses(it)
                    .filter { isClient || it.annotationInfo.all { it.name != ClientOnly::class.java.name } }
                    .safeClasses().toSet()
            }
        getAnnotatedClasses =
            {
                graph.getClassesWithAnnotation(it as Class<out Annotation>)
                    .filter { isClient || it.annotationInfo.all { it.name != ClientOnly::class.java.name } }
                    .safeClasses().toSet()
            }
        getAnnotatedMethods =
            { annotation ->
                val classes = graph.getClassesWithMethodAnnotation(annotation as Class<out Annotation>)

                classes.flatMap { it.methodInfo }
                    .filter { it.annotationInfo.any { it.name == annotation.name } }
                    .filter { isClient || it.annotationInfo.all { it.name != ClientOnly::class.java.name } }
                    .filter { isClient || it.classInfo.annotationInfo.all { it.name != ClientOnly::class.java.name } }
                    .safeMethods(annotation)
                    .toSet()
            }

        HollowModProcessor

        // Очищаем старые результаты сканирования, они в среднем жрут 500мб памяти, так что регистрация аннотаций должна быть одноразовой
        getSubTypes = { emptySet() }
        getAnnotatedClasses = { emptySet() }
        getAnnotatedMethods = { emptySet() }
    }

    fun Collection<MethodInfo>.safeMethods(annotation: Class<*>): List<Method> = mapNotNull {
        try {
            Class.forName(it.className).declaredMethods.filter {
                it.annotations.any { annotation.isInstance(it) }
            }
        } catch (e: NoClassDefFoundError) {
            HollowCore.LOGGER.warn("Class ${it.className} cannot be loaded! ${e.message}")
            null
        } catch (e: ClassNotFoundException) {
            HollowCore.LOGGER.warn("Class ${it.className} cannot be loaded! ${e.message}")
            null
        }
    }.flatten()

    private fun Collection<ClassInfo>.safeClasses(): List<Class<*>> = mapNotNull {
        try {
            Class.forName(it.name)
        } catch (e: Exception) {
            HollowCore.LOGGER.warn("Class ${it.name} cannot be loaded! ${e.message}")
            null
        }
    }
}
//?}