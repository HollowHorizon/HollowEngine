package ru.hollowhorizon.hollowengine.common.attachments.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Entity
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.api.Syncable
import ru.hollowhorizon.hollowengine.common.attachments.api.AttachmentRegistry
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode

@Registerable
@Syncable
@Serializable
@SerialName("hollowengine:entity/hitbox")
data class HitboxComponent(val mode: HitboxMode)

val Entity.hitboxComponent: HitboxComponent?
    get() = AttachmentRegistry.componentsById(this).values.filterIsInstance<HitboxComponent>().firstOrNull()