@file:JvmName("GearyHelper")

package ru.hollowhorizon.hollowengine.common.geary.api

import com.mineinabyss.geary.datatypes.Entity
import com.mineinabyss.geary.datatypes.EntityId
import com.mineinabyss.geary.helpers.entity
import com.mineinabyss.geary.modules.Geary
import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity

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

fun create(level: Level, entity: MCEntity): EntityId = level.geary.entity { set(entity) }.id
fun removeEntity(level: Level, entity: Long) = with(level.geary) { entity.toGeary().removeEntity() }