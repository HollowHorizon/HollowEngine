package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.events.await
import ru.hollowhorizon.hollowengine.common.events.entity.LivingEntityDeathEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent

@Serializable
@SerialName("hollowengine:events/player_join")
class OnPlayerJoinBlock : StartBlock() {
    override val color: Color get() = CodeBlocksColors.EVENTS

    private val player by input<Player>("player")

    override suspend fun trigger() {
        do {
            val event = await<PlayerEvent.Join>()
            val joinedPlayer = event.player
        } while (joinedPlayer != player())
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_on_join".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:events/player_death")
class OnPlayerDeathBlock : StartBlock() {
    override val color: Color get() = CodeBlocksColors.EVENTS

    private val player by input<Player>("player")

    override suspend fun trigger() {
        while (true) {
            val event = await<LivingEntityDeathEvent>()
            if (event.entity == player()) return
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_on_death".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}
