package ru.hollowhorizon.hollowengine.common.story.episode

import net.minecraft.nbt.Tag
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.common.capabilities.CapabilityInstance
import ru.hollowhorizon.hc.common.capabilities.HollowCapabilityV2
import ru.hollowhorizon.hollowengine.common.util.PlayerPermissions

@HollowCapabilityV2(Level::class)
class EpisodesCapability : CapabilityInstance() {
    val episodes by syncableMap<String, Episode>()

    override fun canAcceptFromClient(player: Player, tag: Tag): Boolean {
        return player.hasPermissions(PlayerPermissions.GAMEMASTER)
    }
}