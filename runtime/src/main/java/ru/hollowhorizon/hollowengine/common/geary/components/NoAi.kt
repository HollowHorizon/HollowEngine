package ru.hollowhorizon.hollowengine.common.geary.components

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.Mob
import ru.hollowhorizon.hollowengine.api.Registerable
import ru.hollowhorizon.hollowengine.common.geary.tracking.MCEntity
import java.util.UUID

@Registerable
@Serializable
@SerialName("hollowengine:no_ai")
class NoAi

object NoAiRuntime {
    private val applied = hashSetOf<UUID>()

    fun apply(entity: MCEntity, hasNoAiComponent: Boolean) {
        val mob = entity as? Mob ?: return
        if (hasNoAiComponent) {
            mob.isNoAi = true
            applied += entity.uuid
        } else if (applied.remove(entity.uuid)) {
            mob.isNoAi = false
        }
    }

    fun cleanup(entity: MCEntity) {
        val mob = entity as? Mob ?: return
        if (applied.remove(entity.uuid)) {
            mob.isNoAi = false
        }
    }
}
