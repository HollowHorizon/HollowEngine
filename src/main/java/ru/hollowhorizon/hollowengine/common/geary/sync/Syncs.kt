package ru.hollowhorizon.hollowengine.common.geary.sync

import com.mineinabyss.geary.datatypes.Component
import com.mineinabyss.geary.datatypes.Entity
import com.mineinabyss.geary.helpers.componentId
import com.mineinabyss.geary.serialization.SerializableComponents
import com.mineinabyss.geary.serialization.components.Persists
import kotlinx.serialization.SerializationStrategy
import net.minecraft.nbt.Tag
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.formats
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.nbt
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.serializers
import kotlin.reflect.KClass

data class Syncs(var lastSyncHash: Int = 0)

data class SyncEvent(val location: String, val tag: Tag)

inline fun <reified T : Component> Entity.setSyncing(
    component: T,
    kClass: KClass<out T> = T::class,
    noEvent: Boolean = false,
): T {
    set(component, kClass, noEvent)
    setRelation(world.getAddon(SerializableComponents).persists, world.componentId(kClass), Persists(), noEvent)
    setRelation(world.getAddon(SyncableComponents).syncs, world.componentId(kClass), Syncs(), noEvent)
    emit(1)
    val tag = world.formats.nbt.encode(world.serializers.getSerializerFor(kClass) as SerializationStrategy<T>, component)
    emit(SyncEvent(world.serializers.getSerialNameFor(kClass)!!, tag))
    return component
}