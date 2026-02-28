package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players

import de.fabmax.kool.math.Vec2f
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.api.extensions.PlayerExtension
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.Item
import ru.hollowhorizon.hollowengine.client.kool.addons.InventoryPicker
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.npcs.navigation.block
import ru.hollowhorizon.hollowengine.common.utils.nbt.ForItemStackJson
import ru.hollowhorizon.hollowengine.common.utils.rl

@Serializable
@SerialName("hollowengine:player/get_health")
class PlayerHealthBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val player by input<Player>()

    override suspend fun execute(): Any? {
        return player().health
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_health".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/get_max_health")
class PlayerMaxHealthBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val player by input<Player>()

    override suspend fun execute(): Any? {
        return player().maxHealth
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_max_health".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/heal")
class PlayerHealBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val amount by input<Number>()

    override suspend fun execute() {
        player().heal(amount().toFloat())
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_heal".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("hollowengine.gui.codeblocks.label.player_by".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(amount)
    }
}

@Serializable
@SerialName("hollowengine:player/set_health")
class PlayerSetHealthBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val health by input<Number>()

    override suspend fun execute() {
        player().health = health().toFloat()
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_set_health".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
        Text("hollowengine.gui.codeblocks.label.player_to".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(health)
    }
}

@Serializable
@SerialName("hollowengine:player/give_xp_points")
class PlayerGiveXpPointsBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val amount by input<Number>()

    override suspend fun execute() {
        player().giveExperiencePoints(amount().toInt())
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_give".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(amount)
        Text("hollowengine.gui.codeblocks.label.player_xp_points".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/give_xp_levels")
class PlayerGiveXpLevelsBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val levels by input<Number>()

    override suspend fun execute() {
        player().giveExperienceLevels(levels().toInt())
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_give".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(levels)
        Text("hollowengine.gui.codeblocks.label.player_xp_levels".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/remove_xp_levels")
class PlayerRemoveXpLevelsBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val levels by input<Number>()

    override suspend fun execute() {
        player().giveExperienceLevels(-levels().toInt())
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_remove".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(levels)
        Text("hollowengine.gui.codeblocks.label.player_xp_levels_from".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/get_xp_points")
class GetPlayerXpPointsBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val player by input<Player>()
    override suspend fun execute(): Any? {
        return player().totalExperience
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_xp_points_of".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/get_xp_levels")
class GetPlayerXpLevelsBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val player by input<Player>()
    override suspend fun execute(): Any? {
        return player().experienceLevel
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_xp_levels_of".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/close_gui")
class PlayerCloseGuiBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()

    override suspend fun execute() {
        (player() as PlayerExtension).`hollowcore$closeContainer`()
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_close_gui".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(player)
    }
}

@Serializable
@SerialName("hollowengine:player/set_respawn")
class PlayerSetRespawn : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val player by input<Player>()
    val dimension by input<String>()
    val pos by input<Vec3>()
    val forced by input<Boolean>()
    val sendMessage by input<Boolean>()

    override suspend fun execute() {
        val player = player() as ServerPlayer
        val dimension = ResourceKey.create(Registries.DIMENSION, dimension().rl)
        player.setRespawnPosition(dimension, pos().block, player.yHeadRot, true, false)
    }

    override fun InputSlotScope.composeContent() {
        Column(Grow.Std) {
            var isExpanded by remember(false)
            Row(Grow.Std) {
                Text("hollowengine.gui.codeblocks.label.player_respawn".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                InputSlot(player)
                Text("hollowengine.gui.codeblocks.label.player_to".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                InputSlot(dimension)
                Text("hollowengine.gui.codeblocks.label.player_at".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                InputSlot(pos)
                Box(Grow.Std) {}
                Arrow(if (isExpanded) ArrowScope.ROTATION_DOWN else ArrowScope.ROTATION_RIGHT) {
                    modifier.alignY(AlignmentY.Center)
                        .onClick { isExpanded = !isExpanded }
                }
            }
            if (isExpanded) {
                Row(Grow.Std) {
                    Text("hollowengine.gui.codeblocks.label.player_forced".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                    InputSlot(forced)
                    Text("hollowengine.gui.codeblocks.label.player_send_message".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
                    InputSlot(sendMessage)
                }
            }
        }
    }
}
