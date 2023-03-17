package ru.hollowhorizon.hollowstory.common.capabilities

import kotlinx.serialization.Serializable
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hc.common.capabilities.IHollowCapability
import ru.hollowhorizon.hollowstory.common.entities.NPCEntity
import ru.hollowhorizon.hollowstory.common.npcs.IconType
import ru.hollowhorizon.hollowstory.common.npcs.NPCSettings

@HollowCapabilityV2(NPCEntity::class)
@Serializable
class NPCEntityCapability : IHollowCapability {
    var settings = NPCSettings()
    var iconType = IconType.NONE
}