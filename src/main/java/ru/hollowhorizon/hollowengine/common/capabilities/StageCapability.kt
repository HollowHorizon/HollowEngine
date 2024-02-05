package ru.hollowhorizon.hollowengine.common.capabilities

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2

@HollowCapabilityV2(Player::class)
class PlayerStage: CapabilityInstance() {
    val stages by syncableList<Stage>()
}

@Serializable
class Stage(val name: String)