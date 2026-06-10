package ru.hollowhorizon.hollowengine.common.npcs.dialogues

import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.dialog.DialogGui
import ru.hollowhorizon.hollowengine.common.network.HollowPacket
import ru.hollowhorizon.hollowengine.common.network.HollowPacketHandler
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForEntity

@Serializable
class DialogueScene {
    var character = ""
    var text = ""

    val characters: MutableSet<@Serializable(ForEntity::class) Entity> = mutableSetOf()
    val choices: MutableList<DialogChoice> = mutableListOf()

    fun sync(vararg players: ServerPlayer) {
        UpdateScenePacket(this).send(*players)
    }
}

@Serializable
@HollowPacketHandler(HollowPacketHandler.Direction.TO_CLIENT)
class UpdateScenePacket(
    val scene: DialogueScene
) : HollowPacket {

    companion object {
        private const val LATE_INIT_ERROR = "Minecraft instance is not available"
    }

    override fun handle(player: Player) {
        val minecraft = Minecraft.getInstance()
        val dialogGui = when (val currentScreen = minecraft.screen) {
            is DialogGui -> currentScreen
            else -> {
                val newGui = DialogGui()
                minecraft.setScreen(newGui)
                newGui
            }
        }

        dialogGui.update(scene)
    }
}