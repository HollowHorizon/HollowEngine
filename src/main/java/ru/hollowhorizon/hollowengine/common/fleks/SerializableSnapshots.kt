package ru.hollowhorizon.hollowengine.common.fleks

import com.github.quillraven.fleks.*
import com.github.quillraven.fleks.collection.Bag
import com.github.quillraven.fleks.collection.BitArray

inline val World.entityService: EntityService get() = WorldAccessor.get(this)
inline val EntityService.compMasks: Bag<BitArray> get() = WorldAccessor.getCompMasks(this)
inline val EntityService.delayRemoval: Boolean get() = WorldAccessor.delayRemoval(this)
inline val EntityService.compService: ComponentService get() = WorldAccessor.compService(this)
inline val EntityService.world: World get() = WorldAccessor.world(this)
inline val World.componentService: ComponentService get() = WorldAccessor.getComponentService(this)
inline val World.tagCache: MutableMap<Int, UniqueId<*>> get() = WorldAccessor.getTagCache(this)
inline val World.allFamilies: Array<Family> get() = WorldAccessor.allFamilies(this)
fun Family.onEntityCfgChanged(entity: Entity, compMask: BitArray) = WorldAccessor.onEntityCfgChanged(this, entity, compMask)
fun ComponentService.holderByIndexOrNull(id: Int): ComponentsHolder<*>? = WorldAccessor.holderByIndexOrNull(this, id)
fun ComponentsHolder<*>.setWildcard(entity: Entity, obj: Any) {
    WorldAccessor.setWildcard(this, entity, obj)
}

fun World.snapshotOfSerializable(entity: Entity): Snapshot {
    val comps = mutableListOf<Component<*>>()
    val tags = mutableListOf<UniqueId<*>>()

    if (entity in entityService) {
        entityService.compMasks[entity.id].forEachSetBit { cmpId ->
            val holder = componentService.holderByIndexOrNull(cmpId)
            if (holder == null) {
                // tag instead of a component
                val tag = tagCache[cmpId] ?: throw FleksSnapshotException("Tag with id $cmpId was never assigned")
                tags += tag
            } else {
                val comp = holder[entity]
                FleksPlatform.serializer(comp::class)?.let {
                    comps += comp
                }
            }
        }
    }

    return Snapshot(comps as List<Component<out Any>>, tags as List<UniqueId<out Any>>)
}

fun Snapshot.isEmpty() = components.isEmpty() && tags.isEmpty()
fun Snapshot.isNotEmpty() = !isEmpty()