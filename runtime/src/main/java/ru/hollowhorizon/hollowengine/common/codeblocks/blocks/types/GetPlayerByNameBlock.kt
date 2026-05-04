package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.types

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentServer
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.events.factory.await

@Serializable
@SerialName("hollowengine:player/get_by_name")
class GetPlayerByNameBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Player>()
    val playerName by input<String>("name")

    override suspend fun execute(): Any? {
        val name = playerName()
        val player = currentServer().playerList?.getPlayerByName(name)

        if (player != null) return player

        return PlayerEvent.Join.await { it.player.gameProfile.name == name }.player
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.get_player_by_name".lang) {
            modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center).bold()
        }
        InputSlot(playerName)
    }
}