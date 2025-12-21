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
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:player/set_food")
class PlayerSetFoodBlock : StatementBlock() {
    val entity by input<Player>("player")
    val food by input<Number>("food")

    override suspend fun execute() {
        entity().foodData.foodLevel = food().toInt()
    }

    override fun InputSlotScope.composeContent() {
        Text("Задать сущности") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("уровень сытости") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(food)
    }
}

@Serializable
@SerialName("hollowengine:player/add_exhaustion")
class PlayerAddExhaustionBlock : StatementBlock() {
    val entity by input<Player>("player")
    val exhaustion by input<Number>("exhaustion")

    override suspend fun execute() {
        entity().foodData.addExhaustion(exhaustion().toFloat())
    }

    override fun InputSlotScope.composeContent() {
        Text("Увеличить истощение сущности") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("на") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(exhaustion)
    }
}

@Serializable
@SerialName("hollowengine:player/set_saturation")
class PlayerSetSaturationBlock : StatementBlock() {
    val entity by input<Player>()
    val saturation by input<Number>()

    override suspend fun execute() {
        entity().foodData.setSaturation(saturation().toFloat())
    }

    override fun InputSlotScope.composeContent() {
        Text("Задать сущности") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("уровень насыщения") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(saturation)
    }
}

@Serializable
@SerialName("hollowengine:player/get_saturation")
class PlayerGetSaturationBlock : ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val entity by input<Player>()

    override suspend fun execute(): Any {
        return entity().foodData.saturationLevel
    }

    override fun InputSlotScope.composeContent() {
        Text("Насыщение сущности") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

@Serializable
@SerialName("hollowengine:player/get_food")
class PlayerGetFoodBlock : ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val entity by input<Player>()

    override suspend fun execute(): Any {
        return entity().foodData.foodLevel
    }

    override fun InputSlotScope.composeContent() {
        Text("Сытость сущности") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}