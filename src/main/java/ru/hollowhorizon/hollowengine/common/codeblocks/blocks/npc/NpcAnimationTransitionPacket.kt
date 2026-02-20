package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc

import de.fabmax.kool.math.Easing
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.common.geary.api.entity
import ru.hollowhorizon.hollowengine.common.geary.api.geary
import ru.hollowhorizon.hollowengine.common.geary.components.Model
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler

@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
@Serializable
class NpcAnimationTransitionPacket(
    val entityId: Int,
    val from: String? = null,
    val to: String? = null,
    val duration: Float = 0.33f,
) : HollowPacket {
    override fun handle(player: Player) {
        val level = Minecraft.getInstance().level ?: return
        val entity = level.getEntity(entityId) ?: return

        val gearyEntity = entity.entity
        val model = gearyEntity.get<Model>() ?: return
        val system = model.animationSystem ?: return

        system.scope.launch {
            system.transition(from = from, to = to, duration = duration, easing = Easing.smooth)
        }
    }
}
