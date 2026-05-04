package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.player.Player
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.EventOutputLocalVariableBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.EventDrivenStartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.currentScriptEvent
import ru.hollowhorizon.hollowengine.common.codeblocks.runtime.skipScriptEventExecution
import ru.hollowhorizon.hollowengine.common.events.entity.LivingEntityDeathEvent
import ru.hollowhorizon.hollowengine.common.events.entity.player.PlayerEvent
import ru.hollowhorizon.hollowengine.common.events.factory.await

@Serializable
@SerialName("hollowengine:events/player_join")
class OnPlayerJoinBlock : StartBlock(), EventDrivenStartBlock<PlayerEvent.Join> {
    override val color: Color get() = CodeBlocksColors.EVENTS

    private val playerOutput by outputDefault<Player>(
        name = PLAYER_OUTPUT,
        default = { EventOutputLocalVariableBlock("player") },
    )

    override suspend fun trigger() {
        val event = currentScriptEvent<PlayerEvent.Join>() ?: PlayerEvent.Join.await()
        playerOutput.emit(event.player)
    }

    override val eventType: Class<PlayerEvent.Join> get() = PlayerEvent.Join::class.java

    override fun resolveScopeEntity(event: PlayerEvent.Join) = event.player

    override fun InputSlotScope.composeContent() {
        Column {
            Text("hollowengine.gui.codeblocks.block.on_player_join".lang) {
                modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
            }
            Row(Grow.Std) {
                Text("hollowengine.gui.codeblocks.label.event_player".lang) {
                    modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold().width(Grow.Std)
                }
                OutputSlot(playerOutput)
            }
        }
    }

    companion object {
        const val PLAYER_OUTPUT = "playerOutput"
    }
}

@Serializable
@SerialName("hollowengine:events/player_death")
class OnPlayerDeathBlock : StartBlock(), EventDrivenStartBlock<LivingEntityDeathEvent> {
    override val color: Color get() = CodeBlocksColors.EVENTS

    private val playerOutput by outputDefault<Player>(
        name = PLAYER_OUTPUT,
        default = { EventOutputLocalVariableBlock("player") },
    )
    private val sourceOutput by outputDefault<DamageSource>(
        name = SOURCE_OUTPUT,
        default = { EventOutputLocalVariableBlock("source") },
    )

    override suspend fun trigger() {
        val event = currentScriptEvent<LivingEntityDeathEvent>() ?: LivingEntityDeathEvent.await()
        val player = event.entity as? Player ?: skipScriptEventExecution()
        playerOutput.emit(player)
        sourceOutput.emit(event.source)
    }

    override val eventType: Class<LivingEntityDeathEvent> get() = LivingEntityDeathEvent::class.java

    override fun resolveScopeEntity(event: LivingEntityDeathEvent) = event.entity as? Player

    override fun InputSlotScope.composeContent() {
        Column {
            Text("hollowengine.gui.codeblocks.block.on_player_death".lang) {
                modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
            }
            Row(Grow.Std) {
                Text("hollowengine.gui.codeblocks.label.event_player".lang) {
                    modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold().width(Grow.Std)
                }
                OutputSlot(playerOutput)
            }
            Box { modifier.height(2.dp.scaled()) }
            Row(Grow.Std) {
                Text("hollowengine.gui.codeblocks.label.event_damage_source".lang) {
                    modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold().width(Grow.Std)
                }
                OutputSlot(sourceOutput)
            }
        }
    }

    companion object {
        const val PLAYER_OUTPUT = "playerOutput"
        const val SOURCE_OUTPUT = "sourceOutput"
    }
}
