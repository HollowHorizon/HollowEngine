package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.LivingEntity
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import ru.hollowhorizon.hollowengine.common.utils.angleTo

@Serializable
@SerialName("hollowengine:entity/angle")
class EntityAngleBlock: ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")
    val target by input<LivingEntity>("target")

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()

    override suspend fun execute(): Number {
        return entity().angleTo(target())
    }

    override fun InputSlotScope.composeContent() {
        Text("Угол между") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("и") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}