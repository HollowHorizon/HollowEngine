package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.utils.literal

@Serializable
@SerialName("hollowengine:player/send_message")
class PlayerSendMessageBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val text by input<String>()
    var overlay = false

    override suspend fun execute() {
        val message = text().literal
        player().displayClientMessage(message, overlay)
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_send_message".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)

        ComboBox {
            modifier.width(FitContent).items(listOf("hollowengine.gui.codeblocks.label.player_chat".lang, "hollowengine.gui.codeblocks.label.player_action_bar".lang))
                .alignY(AlignmentY.Center)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.15f), Dimensions.PaddingSmall.scaled()))
                .zLayer(modifier.zLayer + 10)
                .margin(Dimensions.PaddingSmall.scaled()).padding(Dimensions.PaddingSmall.scaled())
                .selectedIndex(if (overlay) 1 else 0)
                .onItemSelected { overlay = it == 1 }
        }

        Text("hollowengine.gui.codeblocks.label.player_text".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(text)
    }
}