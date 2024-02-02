package ru.hollowhorizon.hollowengine.common.capabilities

import kotlinx.serialization.Serializable
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2

@HollowCapabilityV2(ServerLevel::class)
class StructuresCapability: CapabilityInstance() {
    val structures by syncableMap<String, Pos>()
}

@Serializable
class Pos(val x: Int, val y: Int, val z: Int)