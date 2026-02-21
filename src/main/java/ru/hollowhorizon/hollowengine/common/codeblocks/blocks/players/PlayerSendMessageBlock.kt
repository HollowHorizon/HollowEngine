package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.utils.literal

@Serializable
@SerialName("hollowengine:player/send_message")
class PlayerSendMessageBlock : StatementBlock() {
    val player by input<Player>()
    val text by input<String>()
    var overlay = false

    override suspend fun execute() {
        val message = text().literal
        player().displayClientMessage(message, overlay)
    }

    override fun InputSlotScope.composeContent() {
        Text("Сообщение") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)

        ComboBox {
            modifier.width(FitContent).items(listOf("Чат", "Панель действий"))
                .alignY(AlignmentY.Center)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.15f), Dimensions.PaddingSmall.scaled()))
                .zLayer(modifier.zLayer + 10)
                .margin(Dimensions.PaddingSmall.scaled()).padding(Dimensions.PaddingSmall.scaled())
                .selectedIndex(if (overlay) 1 else 0)
                .onItemSelected { overlay = (it == 1) }
        }

        Text("текст:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(text)
    }
}