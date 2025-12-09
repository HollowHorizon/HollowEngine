package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.*
import ru.hollowhorizon.hollowengine.common.utils.currentServer

@Serializable
@SerialName("hollowengine:player/get_by_name")
class GetPlayerByNameBlock : CodeBlock(), ExpressionBlock {
    @Transient
    override val expressionType: ExpressionType = typeOf<Player>()
    val playerName by input<String>("name")

    override suspend fun BlockContext.execute(): Any? {
        val name = playerName()
        return currentServer.playerList.getPlayerByName(name)
            ?: throw IllegalStateException("Player $name not found")
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Игрок с именем") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(playerName)
    }
}