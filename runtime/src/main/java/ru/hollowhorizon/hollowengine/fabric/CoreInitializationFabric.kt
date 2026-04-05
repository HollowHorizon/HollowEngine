//? if fabric {
package ru.hollowhorizon.hollowengine.fabric

import net.fabricmc.api.EnvType
import net.fabricmc.loader.api.FabricLoader
import ru.hollowhorizon.hollowengine.HollowCore
import ru.hollowhorizon.hollowengine.common.events.ClientOnly
import ru.hollowhorizon.hollowengine.common.runtime.RuntimeAnnotationEnvironment
import ru.hollowhorizon.hollowengine.common.runtime.loadBootstrapOrRuntimeClass
import ru.hollowhorizon.hollowengine.common.runtime.resolve
import ru.hollowhorizon.hollowengine.common.registry.HollowModProcessor
import ru.hollowhorizon.hollowengine.common.registry.getAnnotatedClasses
import ru.hollowhorizon.hollowengine.common.registry.getAnnotatedMethods
import ru.hollowhorizon.hollowengine.common.registry.getSubTypes

object CoreInitializationFabric {
    init {
        val isClient = FabricLoader.getInstance().environmentType == EnvType.CLIENT

        getSubTypes =
            { superType ->
                RuntimeAnnotationEnvironment.annotationIndex
                    .getSubTypes(superType.name, isClient)
                    .mapNotNull { loadBootstrapOrRuntimeClass(it, HollowCore::class.java.classLoader) }
                    .filter { isClient || !it.isAnnotationPresent(ClientOnly::class.java) }
                    .toSet()
            }
        getAnnotatedClasses =
            { annotation ->
                RuntimeAnnotationEnvironment.annotationIndex
                    .getAnnotatedClasses(annotation.name, isClient)
                    .mapNotNull { loadBootstrapOrRuntimeClass(it, HollowCore::class.java.classLoader) }
                    .filter { isClient || !it.isAnnotationPresent(ClientOnly::class.java) }
                    .toSet()
            }
        getAnnotatedMethods =
            { annotation ->
                RuntimeAnnotationEnvironment.annotationIndex
                    .getAnnotatedMethods(annotation.name, isClient)
                    .mapNotNull { it.resolve(HollowCore::class.java.classLoader) }
                    .filter { isClient || !it.isAnnotationPresent(ClientOnly::class.java) }
                    .toSet()
            }

        HollowModProcessor

        // Очищаем старые результаты сканирования, они в среднем жрут 500мб памяти, так что регистрация аннотаций должна быть одноразовой
        getSubTypes = { emptySet() }
        getAnnotatedClasses = { emptySet() }
        getAnnotatedMethods = { emptySet() }
    }
}
//?}
