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
@SerialName("hollowengine:player/set_absorption")
class PlayerSetAbsorption : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.PLAYERS

    val entity by input<Player>("player")
    val absorption by input<Number>("absorption")

    override suspend fun execute() {
        entity().absorptionAmount = absorption().toFloat()
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_set".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.player_absorption_level".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(absorption)
    }
}

@Serializable
@SerialName("hollowengine:player/get_absorption")
class PlayerGetAbsorption : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.NPCS

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val entity by input<Player>()

    override suspend fun execute(): Any {
        return entity().absorptionAmount
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.player_absorption_of".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}
