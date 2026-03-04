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
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.EventDrivenStartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentScriptEvent
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.skipScriptEventExecution
import ru.hollowhorizon.hollowengine.common.events.await
import ru.hollowhorizon.hollowengine.common.events.entity.LivingEntityDeathEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent

@Serializable
@SerialName("hollowengine:events/player_join")
class OnPlayerJoinBlock : StartBlock(), EventDrivenStartBlock<PlayerEvent.Join> {
    override val color: Color get() = CodeBlocksColors.EVENTS

    private val player by input<Player>("player")

    override suspend fun trigger() {
        currentScriptEvent<PlayerEvent.Join>()?.let { event ->
            if (event.player != player()) skipScriptEventExecution()
            return
        }
        do {
            val event = await<PlayerEvent.Join>()
            val joinedPlayer = event.player
        } while (joinedPlayer != player())
    }

    override val eventType: Class<PlayerEvent.Join> get() = PlayerEvent.Join::class.java

    override fun resolveScopeEntity(event: PlayerEvent.Join) = event.player

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_on_join".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:events/player_death")
class OnPlayerDeathBlock : StartBlock(), EventDrivenStartBlock<LivingEntityDeathEvent> {
    override val color: Color get() = CodeBlocksColors.EVENTS

    private val player by input<Player>("player")

    override suspend fun trigger() {
        currentScriptEvent<LivingEntityDeathEvent>()?.let { event ->
            if (event.entity != player()) skipScriptEventExecution()
            return
        }
        while (true) {
            val event = await<LivingEntityDeathEvent>()
            if (event.entity == player()) return
        }
    }

    override val eventType: Class<LivingEntityDeathEvent> get() = LivingEntityDeathEvent::class.java

    override fun resolveScopeEntity(event: LivingEntityDeathEvent) = event.entity

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_on_death".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}
