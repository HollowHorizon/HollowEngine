package ru.hollowhorizon.hollowengine.common.graph

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import java.util.*

@Serializable
class SerializedEntity(
    val uuid: @Serializable(ForUuid::class) UUID,
    val level: @Serializable(ForResourceLocation::class) ResourceLocation,
)

inline fun <reified T : Entity> GraphContext.rememberEntity(name: String? = null, crossinline default: suspend () -> T): Variable<T> {
    // Сама переменная хранит идентификатор и измерение сущности
    val value: Variable<SerializedEntity> = remember(name) {
        val entity = default()
        SerializedEntity(entity.uuid, entity.level().dimension().location())
    }
    rememberVariables -= value
    // Но в результате мы хотим получить объект этой сущности (который не может быть десериализован без suspend)
    return value.map({
        val level = currentServer.getLevel(ResourceKey.create(Registries.DIMENSION, it.level))
            ?: error("Level ${it.level} not found!")

        while (true) {
            val entity = level.getEntity(it.uuid)
            if (entity != null) return@map entity as T

            delay(50) // Проверяем каждый тик, прогрузилась ли сущность
        }
        error("Entity ${it.uuid} not found!")
    }, { // Если сущность изменили через делегат, то сериализованную нужно тоже изменить
        SerializedEntity(it.uuid, it.level().dimension().location())
    }).apply {
        rememberVariables += this
    }
}