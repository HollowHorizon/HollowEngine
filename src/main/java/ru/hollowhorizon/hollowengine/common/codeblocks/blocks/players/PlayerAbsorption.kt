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
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:player/set_absorption")
class PlayerSetAbsorption : StatementBlock() {
    val entity by input<Player>("player")
    val absorption by input<Number>("absorption")

    override suspend fun BlockContext.execute() {
        entity().absorptionAmount = absorption().toFloat()
    }

    override fun InputSlotScope.composeContent() {
        Text("Задать") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("уровень поглощения") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(absorption)
    }
}

@Serializable
@SerialName("hollowengine:player/get_absorption")
class PlayerGetAbsorption : ExpressionBlock() {
    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val entity by input<Player>()

    override suspend fun BlockContext.execute(): Any {
        return entity().absorptionAmount
    }

    override fun InputSlotScope.composeContent() {
        Text("Уровень поглощения") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}