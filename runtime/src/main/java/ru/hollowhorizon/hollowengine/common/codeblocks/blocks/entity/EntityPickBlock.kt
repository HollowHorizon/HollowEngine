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
import net.minecraft.world.phys.Vec3
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:entity/raycast")
class EntityPickBlock: ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")
    val distance by input<Number>("distance")

    @Transient
    override val expressionType: ExpressionType = typeOf<Vec3>()

    override suspend fun execute(): Vec3 {
        val entity = entity()
        return entity.pick(distance().toDouble(), 0f, false).location
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.label.entity_raycast".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity_raycast_distance".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(distance)
    }
}