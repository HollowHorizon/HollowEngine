package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.EventOutputLocalVariableBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.EventDrivenStartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentScriptEvent
import ru.hollowhorizon.hollowengine.common.events.await
import ru.hollowhorizon.hollowengine.common.events.server.ServerChatEvent

@Serializable
@SerialName("hollowengine:events/player_chat")
class OnPlayerChatBlock : StartBlock(), EventDrivenStartBlock<ServerChatEvent> {
    override val color: Color get() = CodeBlocksColors.EVENTS
    private val playerOutput by outputDefault<Player>(
        name = PLAYER_OUTPUT,
        default = { EventOutputLocalVariableBlock("player") },
    )
    private val messageOutput by outputDefault<String>(
        name = MESSAGE_OUTPUT,
        default = { EventOutputLocalVariableBlock("message") },
    )
    private val usernameOutput by outputDefault<String>(
        name = USERNAME_OUTPUT,
        default = { EventOutputLocalVariableBlock("username") },
    )

    override suspend fun trigger() {
        val event = currentScriptEvent<ServerChatEvent>() ?: await<ServerChatEvent>()
        playerOutput.emit(event.player)
        messageOutput.emit(event.message.string)
        usernameOutput.emit(event.username)
    }

    override val eventType: Class<ServerChatEvent> get() = ServerChatEvent::class.java

    override fun resolveScopeEntity(event: ServerChatEvent) = event.player

    override fun InputSlotScope.composeContent() {
        Column {
            Text("hollowengine.gui.codeblocks.block.on_player_chat".lang) {
                modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
            }
            Row(Grow.Std) {
                Text("hollowengine.gui.codeblocks.label.event_player".lang) {
                    modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
                        .width(Grow.Std)
                }
                OutputSlot(playerOutput)
            }
            Box { modifier.height(2.dp.scaled()) }
            Row(Grow.Std) {
                Text("hollowengine.gui.codeblocks.label.event_message".lang) {
                    modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
                        .width(Grow.Std)
                }
                OutputSlot(messageOutput)
            }
            Box { modifier.height(2.dp.scaled()) }
            Row(Grow.Std) {
                Text("hollowengine.gui.codeblocks.label.event_username".lang) {
                    modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
                        .width(Grow.Std)
                }
                OutputSlot(usernameOutput)
            }
        }
    }

    companion object {
        const val PLAYER_OUTPUT = "playerOutput"
        const val MESSAGE_OUTPUT = "messageOutput"
        const val USERNAME_OUTPUT = "usernameOutput"
    }
}
