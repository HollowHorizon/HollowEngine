package ru.hollowhorizon.hollowengine.common.fleks.lookup

import com.github.quillraven.fleks.*
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import ru.hollowhorizon.hollowengine.common.fleks.components.EntityComponent

class MinecraftEntityLookup(private val world: World) {
    private val idMap = Int2ObjectOpenHashMap<Entity>()

    fun getOrCreateById(mcEntityId: Int): Entity {
        return idMap.getOrPut(mcEntityId) {
            world.entity {
                it += MinecraftLink(mcEntityId)
                it += PendingSpawnTag
            }
        }
    }

    fun linkWithMinecraft(mcEntity: net.minecraft.world.entity.Entity): Entity {
        val entity = getOrCreateById(mcEntity.id)

        with(world) {
            entity.configure {
                it -= PendingSpawnTag
                it += EntityComponent(mcEntity)
            }
        }
        return entity
    }

    fun remove(mcEntityId: Int) {
        val ecsEntity = idMap.remove(mcEntityId)
        if (ecsEntity != null) {
            world -= ecsEntity
        }
    }

    fun changeId(entity: Entity, oldId: Int, mcId: Int) {
        if(idMap.remove(oldId) != null) {
            idMap[mcId] = entity
        }
    }
}

class MinecraftLink(var id: Int) : Component<MinecraftLink> {
    override fun type() = MinecraftLink

    companion object : ComponentType<MinecraftLink>()
}

object PendingSpawnTag : EntityTag()