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
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.events.await
import ru.hollowhorizon.hollowengine.common.events.entity.LivingEntityDeathEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent

@Serializable
@SerialName("hollowengine:events/player_join")
class OnPlayerJoinBlock : StatementBlock(), StartBlock {
    private val player by input<Player>("player")

    override suspend fun execute() {
        do {
            val event = await<PlayerEvent.Join>()
            val joinedPlayer = event.player
        } while (joinedPlayer != player())
    }

    override fun InputSlotScope.composeContent() {
        Text("Когда заходит") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:events/player_death")
class OnPlayerDeathBlock : StatementBlock(), StartBlock {
    private val player by input<Player>("player")

    override suspend fun execute() {
        while (true) {
            val event = await<LivingEntityDeathEvent>()
            if (event.entity == player()) return
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("Когда умирает") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}