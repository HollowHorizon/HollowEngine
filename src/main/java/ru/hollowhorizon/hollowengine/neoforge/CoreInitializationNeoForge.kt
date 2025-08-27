package ru.hollowhorizon.hollowengine.neoforge;

//? if neoforge {

/*import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.fml.loading.FMLLoader
import net.neoforged.neoforgespi.language.ModFileScanData
import org.objectweb.asm.Type
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.registry.HollowModProcessor
import ru.hollowhorizon.hollowengine.common.registry.getAnnotatedClasses
import ru.hollowhorizon.hollowengine.common.registry.getAnnotatedMethods
import ru.hollowhorizon.hollowengine.common.registry.getSubTypes
import java.lang.annotation.ElementType
import java.lang.reflect.Method


object CoreInitializationNeoForge {
    init {
        val scanInfo = ModList.get().mods
            .filter { mod -> mod.dependencies.any { it.modId == HollowCore.MODID } || mod.modId == HollowCore.MODID }
            .map { it.owningFile.file.scanResult }
        val classes = scanInfo.flatMap { it.classes }
        val annotations = scanInfo.flatMap { it.annotations }

        val isClient = FMLEnvironment.dist.isClient

        getSubTypes = { subType ->
            classes
                .filter { it.parent.className == subType.name }
                .filter { isClient || !it.clazz.hasAnnotation(ClientOnly::class.java) }
                .safeClassesF().toSet()
        }
        getAnnotatedClasses = { annotation ->
            annotations
                .filter { it.annotationType.className == annotation.name }
                .filter { it.targetType == ElementType.TYPE }
                .filter { isClient || !it.clazz.hasAnnotation(ClientOnly::class.java) }
                .safeClasses()
                .toSet()
        }
        getAnnotatedMethods = { annotation ->
            annotations
                .filter { it.annotationType.className == annotation.name }
                .filter { it.targetType == ElementType.METHOD }
                .filter { isClient || !it.clazz.hasAnnotation(ClientOnly::class.java) }
                .safeMethods()
                .toSet()
        }
        HollowModProcessor

        getSubTypes = { emptySet() }
        getAnnotatedClasses = { emptySet() }
        getAnnotatedMethods = { emptySet() }
    }

    fun Collection<ModFileScanData.AnnotationData>.safeMethods(): List<Method> = mapNotNull {
        try {
            val name = it.memberName.substringBefore('(')
            Class.forName(it.clazz.className, false, HollowCore::class.java.classLoader).declaredMethods
                .filter { m -> m.name == name }
        } catch (e: NoClassDefFoundError) {
            HollowCore.LOGGER.warn("Class ${it.clazz.className} cannot be loaded! ${e.message}")
            null
        } catch (e: ClassNotFoundException) {
            HollowCore.LOGGER.warn("Class ${it.clazz.className} cannot be loaded! ${e.message}")
            null
        }
    }.flatten()

    private fun Collection<ModFileScanData.AnnotationData>.safeClasses(): List<Class<*>> = mapNotNull {
        try {
            Class.forName(it.clazz.className, false, HollowCore::class.java.classLoader)
        } catch (e: NoClassDefFoundError) {
            HollowCore.LOGGER.warn("Class ${it.clazz.className} cannot be loaded! ${e.message}")
            null
        }
    }
    private fun Collection<ModFileScanData.ClassData>.safeClassesF(): List<Class<*>> = mapNotNull {
        try {
            Class.forName(it.clazz.className, false, HollowCore::class.java.classLoader)
        } catch (e: NoClassDefFoundError) {
            HollowCore.LOGGER.warn("Class ${it.clazz.className} cannot be loaded! ${e.message}")
            null
        }
    }
}
private fun Type.hasAnnotation(java: Class<out Annotation>): Boolean {
    return Class.forName(className, false, HollowCore::class.java.classLoader).isAnnotationPresent(java)
}

*///?}