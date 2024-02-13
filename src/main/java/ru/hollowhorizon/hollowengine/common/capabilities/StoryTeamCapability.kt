package ru.hollowhorizon.hollowengine.common.capabilities

import dev.ftb.mods.ftbteams.data.Team
import dev.ftb.mods.ftbteams.data.TeamBase
import kotlinx.serialization.Serializable
import net.minecraft.resources.ResourceLocation
import ru.hollowhorizon.hc.client.utils.nbt.ForResourceLocation
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2

@HollowCapabilityV2(TeamBase::class)
class StoryTeamCapability : CapabilityInstance() {
    val aimMarks by syncableList<AimMark>()
}

@Serializable
class AimMark(
    val x: Double,
    val y: Double,
    val z: Double,
    val icon: @Serializable(ForResourceLocation::class) ResourceLocation,
    val ignoreY: Boolean
)