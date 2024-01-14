package ru.hollowhorizon.hollowengine.common.capabilities

import net.minecraft.server.level.ServerLevel
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2

@HollowCapabilityV2(ServerLevel::class)
class StructuresCapability: CapabilityInstance() {
    val structures by syncableList<String>()
}