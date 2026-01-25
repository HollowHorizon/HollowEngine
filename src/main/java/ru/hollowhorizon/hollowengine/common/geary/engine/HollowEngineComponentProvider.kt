package ru.hollowhorizon.hollowengine.common.geary.engine

import co.touchlab.kermit.Logger
import com.mineinabyss.geary.components.ReservedComponents
import com.mineinabyss.geary.datatypes.ComponentId
import com.mineinabyss.geary.engine.ComponentProvider
import com.mineinabyss.geary.engine.EntityProvider
import net.minecraft.world.level.Level
import kotlin.reflect.KClassifier

class HollowEngineComponentProvider(
    val level: Level,
    val entityProvider: EntityProvider,
    val logger: Logger,
) : ComponentProvider {
    private val classToComponentMap = mutableMapOf<KClassifier, Long>()
    private val componentToClassMap = mutableMapOf<Long, KClassifier>()

    init {
        createReservedComponents()
    }

    override fun getOrRegisterComponentIdForClass(kClass: KClassifier): ComponentId {
        val id = classToComponentMap.getOrElse(kClass) {
            return registerComponentIdForClass(kClass)
        }
        return id.toULong()
    }

    private fun registerComponentIdForClass(kClass: KClassifier): ComponentId {
        logger.v("Registering new component: $kClass")
        val compEntity = entityProvider.create()
        classToComponentMap[kClass] = compEntity.toLong()
        componentToClassMap[compEntity.toLong()] = kClass
        return compEntity
    }

    private fun createReservedComponents() {
        logger.v("Creating reserved components")
        ReservedComponents.reservedComponents.forEach { (kClass, id) ->
            classToComponentMap[kClass] = id.toLong()
            componentToClassMap[id.toLong()] = kClass
        }
    }

    operator fun get(id: ComponentId) = componentToClassMap[id.toLong()]
}
