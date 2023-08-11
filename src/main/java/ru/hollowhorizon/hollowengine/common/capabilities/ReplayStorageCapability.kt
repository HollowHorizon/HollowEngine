package ru.hollowhorizon.hollowengine.common.capabilities

import kotlinx.serialization.Serializable
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.common.capabilities.HollowCapability
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hollowengine.cutscenes.replay.Replay
import ru.hollowhorizon.hollowengine.cutscenes.replay.ReplayPlayer

@HollowCapabilityV2(Level::class)
@Serializable
class ReplayStorageCapability : HollowCapability() {
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