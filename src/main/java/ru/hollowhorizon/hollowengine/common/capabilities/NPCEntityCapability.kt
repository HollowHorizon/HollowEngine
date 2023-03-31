package ru.hollowhorizon.hollowengine.common.capabilities

import kotlinx.serialization.Serializable
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hc.common.capabilities.HollowCapability
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.IconType
import ru.hollowhorizon.hollowengine.common.npcs.NPCSettings

@HollowCapabilityV2(NPCEntity::class)
@Serializable
class NPCEntityCapability : HollowCapability() {
    var settings = NPCSettings()
    var iconType = IconType.NONE
}