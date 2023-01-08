package ru.hollowhorizon.hollowstory.common.capabilities

import kotlinx.serialization.Serializable
import net.minecraft.world.World
import ru.hollowhorizon.hc.common.capabilities.HollowCapability
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hollowstory.cutscenes.replay.Replay
import ru.hollowhorizon.hollowstory.cutscenes.replay.ReplayPlayer

@HollowCapabilityV2(World::class)
@Serializable
class ReplayStorageCapability : HollowCapability<ReplayStorageCapability>() {
    private val replays: HashMap<String, Replay> = HashMap()

    fun addReplay(name: String, replay: Replay) {
        replays[name] = replay
    }

    fun getAllReplays(): HashMap<String, Replay> {
        return replays
    }

    fun getReplay(name: String): Replay {
        return replays[name] ?: throw IllegalArgumentException("Replay with name $name not found")
    }
}

val ACTIVE_REPLAYS = arrayListOf<ReplayPlayer>()