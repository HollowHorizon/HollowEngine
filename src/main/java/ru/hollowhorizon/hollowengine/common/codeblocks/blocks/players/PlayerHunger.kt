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
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:player/set_food")
class PlayerSetFoodBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val entity by input<Player>("player")
    val food by input<Number>("food")

    override suspend fun execute() {
        entity().foodData.foodLevel = food().toInt()
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_set_entity".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.player_food_level".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(food)
    }
}

@Serializable
@SerialName("hollowengine:player/add_exhaustion")
class PlayerAddExhaustionBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val entity by input<Player>("player")
    val exhaustion by input<Number>("exhaustion")

    override suspend fun execute() {
        entity().foodData.addExhaustion(exhaustion().toFloat())
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_add_exhaustion".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.player_by".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(exhaustion)
    }
}

@Serializable
@SerialName("hollowengine:player/set_saturation")
class PlayerSetSaturationBlock : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val entity by input<Player>()
    val saturation by input<Number>()

    override suspend fun execute() {
        entity().foodData.setSaturation(saturation().toFloat())
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_set_entity".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.player_saturation_level".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(saturation)
    }
}

@Serializable
@SerialName("hollowengine:player/get_saturation")
class PlayerGetSaturationBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val entity by input<Player>()

    override suspend fun execute(): Any {
        return entity().foodData.saturationLevel
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_saturation_of".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:player/get_food")
class PlayerGetFoodBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val entity by input<Player>()

    override suspend fun execute(): Any {
        return entity().foodData.foodLevel
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_food_of".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}
