@file:JvmName("GearyHelper")

package ru.hollowhorizon.hollowengine.common.geary.api

import com.mineinabyss.geary.datatypes.Entity
import com.mineinabyss.geary.datatypes.EntityId
import com.mineinabyss.geary.modules.Geary
import com.mineinabyss.geary.modules.get
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import ru.hollowhorizon.hollowengine.common.geary.tracking.MinecraftEntityLookup
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.decodeComponents
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.encodeComponentsTo
import ru.hollowhorizon.hollowengine.common.geary.tracking.datastore.loadComponentsFrom

interface GearyProvider {
    val `hollowengine$geary`: Geary
}

interface EntityProvider {
    val `hollowengine$entity`: Long
}

val Level.geary: Geary
    get() = (this as GearyProvider).`hollowengine$geary`

val MCEntity.entityId: Long
    get() = (this as EntityProvider).`hollowengine$entity`

val MCEntity.entity: Entity
    get() = with(level().geary) { entityId.toGeary() }

fun create(level: Level, entity: MCEntity): EntityId =
    level.geary.get<MinecraftEntityLookup>().linkWithMinecraft(level, entity)

// TODO: Наверное должен быть более удобный способ переместить сущность из одного мира в другой?
fun move(old: Level, new: Level, entity: Long, mcEntity: MCEntity): EntityId {
    val tag = CompoundTag()
    with(old.geary) { entity.encodeComponentsTo(tag) }
    with(new.geary) {
        val newEntity = create(new, mcEntity)
        newEntity.toGeary().loadComponentsFrom(tag.decodeComponents())
        return newEntity
    }
}

fun removeEntity(level: Level, entity: Int) = level.geary.get<MinecraftEntityLookup>().remove(entity)
fun changeId(level: Level, oldId: Int, newId: Int) = level.geary.get<MinecraftEntityLookup>().changeId(level, oldId, newId)