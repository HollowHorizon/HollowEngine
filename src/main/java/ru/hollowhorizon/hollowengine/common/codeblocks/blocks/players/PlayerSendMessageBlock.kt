package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.utils.literal

@Serializable
@SerialName("hollowengine:player/send_message")
class PlayerSendMessageBlock : CodeBlock() {
    val player by input<Player>()
    val text by input<String>()
    var overlay = false

    override suspend fun BlockContext.execute() {
        val message = text().literal
        player().displayClientMessage(message, overlay)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Сообщение") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)

        ComboBox {
            modifier.width(FitContent).items(listOf("Чат", "Экран"))
            modifier.selectedIndex(if (overlay) 1 else 0)
            modifier.onItemSelected { overlay = (it == 1) }
        }

        Text("текст:") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(text)
    }
}