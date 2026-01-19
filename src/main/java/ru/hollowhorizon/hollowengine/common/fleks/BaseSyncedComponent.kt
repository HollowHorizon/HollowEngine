package ru.hollowhorizon.hollowengine.common.fleks

import com.github.quillraven.fleks.Component
import com.github.quillraven.fleks.Entity
import com.github.quillraven.fleks.Snapshot
import com.github.quillraven.fleks.World
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.fleks.components.EntityComponent
import ru.hollowhorizon.hollowengine.common.network.sendTrackingEntityAndSelf

@Serializable
abstract class BaseSyncedComponent<T> : SyncedComponent<T> {
    override fun World.onAdd(entity: Entity) {
        val level = inject<Level>()
        if (shouldSync() && !level.isClientSide) {
            val mcEntity = entity[EntityComponent].entity
            ComponentUpdatePacket(mcEntity.id, Snapshot(listOf(this@BaseSyncedComponent as Component<out @Contextual Any>), listOf()))
                .sendTrackingEntityAndSelf(mcEntity)
        }
    }

    override fun World.onRemove(entity: Entity) {
        val level = inject<Level>()
        if (shouldSync() && !level.isClientSide) {
            val mcEntity = entity[EntityComponent].entity
            ComponentRemovePacket(mcEntity.id, type().id)
                .sendTrackingEntityAndSelf(mcEntity)
        }
    }
}

@Serializable
abstract class MutableSyncedComponent<out T: Any> : BaseSyncedComponent<@UnsafeVariance T>() {
    @Transient
    private var dirty: Boolean = true

    override fun World.onAdd(entity: Entity) {
        val system = system<ComponentSyncSystem>()
        system.startTracking(entity, this@MutableSyncedComponent)
    }

    override fun World.onRemove(entity: Entity) {
        val system = system<ComponentSyncSystem>()
        system.stopTracking(entity, this@MutableSyncedComponent)
    }

    protected fun markDirty() {
        dirty = true
    }

    internal fun resetDirty() {
        dirty = false
    }

    override fun shouldSync(): Boolean {
        return dirty
    }
}