package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.GameType
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

@Serializable
@SerialName("hollowengine:player/gamemode")
class PlayerGameModeBlock : StatementBlock() {
    val player by input<Player>()
    var modeInt = 0 // 0=Survival, 1=Creative, 2=Adventure, 3=Spectator

    override suspend fun BlockContext.execute() {
        val p = player()
        if (p is ServerPlayer) {
            val mode = when (modeInt) {
                1 -> GameType.CREATIVE
                2 -> GameType.ADVENTURE
                3 -> GameType.SPECTATOR
                else -> GameType.SURVIVAL
            }
            p.setGameMode(mode)
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("Режим игры") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)

        ComboBox {
            modifier.width(FitContent).items(listOf("Выживание", "Творческий", "Приключение", "Наблюдатель"))
            modifier.selectedIndex(modeInt)
            modifier.onItemSelected { modeInt = it }
        }
    }
}