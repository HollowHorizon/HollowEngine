package ru.hollowhorizon.hollowengine.common.graph

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.common.utils.currentServer
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForUuid
import ru.hollowhorizon.hollowengine.common.utils.nbt.NBTFormat
import ru.hollowhorizon.hollowengine.common.utils.serialization.deserialize
import ru.hollowhorizon.hollowengine.common.utils.serialization.serialize
import java.util.*
import kotlin.reflect.KProperty

@Serializable
class SerializedEntity(
    val uuid: @Serializable(ForUuid::class) UUID,
    val level: @Serializable(ForResourceLocation::class) ResourceLocation,
)

inline fun <reified T : Entity> GraphContext.rememberEntity(name: String? = null, crossinline default: suspend () -> T): Variable<T> {
    return object : Variable<T> {
        private var name: String? = name
        private var value: T? = null


        override suspend fun deserialize(tag: Tag?): T {
            if(tag == null) return default().also { value = it }

            val it: SerializedEntity = NBTFormat.deserialize(tag)

            val level = currentServer.getLevel(ResourceKey.create(Registries.DIMENSION, it.level))
                ?: error("Level ${it.level} not found!")

            while (true) {
                val entity = level.getEntity(it.uuid)
                if (entity != null) return entity as T

                delay(50)
            }
        }

        override fun serialize(): Tag? {
            return value?.let { NBTFormat.serialize(SerializedEntity(it.uuid, it.level().dimension().location())) }
        }

        override fun name(): String {
            return this.name ?: error("Variable name not found!")
        }

        override fun provideDelegate(
            thisRef: Any?,
            property: KProperty<*>,
        ): Variable<T> {
            if(this.name == null) this.name = property.name
            return this
        }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T {
            return value ?: error("Variable $name value not found!")
        }

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            this.value = value
        }

    }.apply {
        this@rememberEntity.rememberVariables += this
    }
}