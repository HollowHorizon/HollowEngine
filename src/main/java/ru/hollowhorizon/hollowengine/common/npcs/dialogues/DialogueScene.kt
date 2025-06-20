package ru.hollowhorizon.hollowengine.common.npcs.dialogues

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hc.common.utils.nbt.ForEntity
import ru.hollowhorizon.hc.common.network.HollowPacket
import ru.hollowhorizon.hc.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.client.gui.dialog.DialogGui

@Serializable
class DialogueScene {
    var character = ""
    var text = ""

    val characters = mutableSetOf<@Serializable(ForEntity::class) Entity>()
    val choices = mutableListOf<DialogChoice>()

    fun sync(vararg players: ServerPlayer) {
        UpdateScenePacket(this).send(*players)
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class UpdateScenePacket(val scene: DialogueScene) : HollowPacket {
    override fun handle(player: Player) {
        val screen = Minecraft.getInstance().screen as? DialogGui ?: DialogGui().apply { Minecraft.getInstance().setScreen(this) }

        screen.update(scene)
    }
}

