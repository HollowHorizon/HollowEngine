package ru.hollowhorizon.hollowengine.client.gui

import de.fabmax.kool.scene.Scene
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.client.models.internal.Transform
import ru.hollowhorizon.hc.client.models.internal.animations.AnimationType
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.utils.colored
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hc.common.network.HollowPacketV2
import ru.hollowhorizon.hc.common.network.HollowPacketV3
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.npcs.HitboxMode
import ru.hollowhorizon.hollowengine.common.npcs.NPCCapability

class NPCCreatorGui(val npc: NPCEntity, private val npcId: Int) : KoolGui {

    override fun Scene.setup() {

    }
}

@HollowPacketV2
@Serializable
class NPCCreatorPacket(
    private val id: Int,
    private val name: String,
    private val model: String,
    private val showName: Boolean,
    private val switchHeadRot: Boolean,
    private val invulnerable: Boolean,
    private val hitboxWidth: Float,
    private val hitboxHeight: Float,
    private val hitboxMode: HitboxMode,
    private val animations: Map<AnimationType, String>,
    private val textures: Map<String, String>,
    private val tX: Float, private val tY: Float, private val tZ: Float,
    private val rX: Float, private val rY: Float, private val rZ: Float,
    private val sX: Float, private val sY: Float, private val sZ: Float,
) : HollowPacketV3<NPCCreatorPacket> {
    override fun handle(player: Player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage("У вас не достаточно прав для этого действия!".mcText)
            return
        }

        val entity = player.level().getEntity(id) as? NPCEntity

        if (entity == null) {
            player.sendSystemMessage("Ошибка, персонаж не был заспавнен!".mcText.colored(0xFF2222))
            return
        }

        entity[AnimatedEntityCapability::class].apply {
            model = this@NPCCreatorPacket.model
            animations.putAll(this@NPCCreatorPacket.animations)
            textures.putAll(this@NPCCreatorPacket.textures)
            transform = Transform(tX, tY, tZ, rX, rY, rZ, sX, sY, sZ)
            switchHeadRot = this@NPCCreatorPacket.switchHeadRot
        }
        entity[NPCCapability::class].hitboxMode = hitboxMode

        entity.isInvulnerable = invulnerable
        entity.isCustomNameVisible = showName && this@NPCCreatorPacket.name.isNotEmpty()
        entity.customName = name.mcText
        entity.setDimensions(hitboxWidth to hitboxHeight)
        entity.refreshDimensions()
    }


}