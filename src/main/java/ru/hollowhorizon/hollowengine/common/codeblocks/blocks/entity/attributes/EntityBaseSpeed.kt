package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity.attributes

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import java.util.*

@Serializable
@SerialName("hollowengine:entity/set_speed_modifier")
class EntitySetBaseSpeed : StatementBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    val entity by input<LivingEntity>("entity")
    val speed by input<Number>("speed")

    override suspend fun execute() {
        val entity = entity()
        val attribute = entity.attributes.getInstance(Attributes.MOVEMENT_SPEED) ?: return

        //? if > 1.20.1 {
        /*val modifier = AttributeModifier(
            "hollowengine:speed_modifier".rl,
            speed().toDouble(),
            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        )
        if (attribute.hasModifier(modifier.id)) attribute.removeModifier(modifier)
        attribute.addPermanentModifier(modifier)
        *///?} else {
        val modifier = AttributeModifier(
            MODIFIER,
            "HollowEngine Base Speed Modifier",
            speed().toDouble(),
            AttributeModifier.Operation.MULTIPLY_TOTAL
        )
        if (attribute.hasModifier(modifier)) attribute.removeModifier(modifier)
        attribute.addPermanentModifier(modifier)
        //?}
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.set_speed_modifier".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
        Text("hollowengine.gui.codeblocks.label.entity".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(speed)
    }
}

@Serializable
@SerialName("hollowengine:player/get_speed_modifier")
class EntityGetBaseSpeed : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.ENTITIES

    @Transient
    override val expressionType: ExpressionType = typeOf<Number>()
    val entity by input<LivingEntity>()

    override suspend fun execute(): Any? {
        //? if > 1.20.1 {
        /*return entity().attributes.getInstance(Attributes.MOVEMENT_SPEED)?.getModifier("hollowengine:speed_modifier".rl)?.amount ?: 1.0
        *///?} else {
        return entity().attributes.getInstance(Attributes.MOVEMENT_SPEED)?.getModifier(MODIFIER)?.amount ?: 1.0
        //?}
    }

    override fun InputSlotScope.composeContent() {
        Text("hollowengine.gui.codeblocks.block.get_speed_modifier".lang) { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(entity)
    }
}

val MODIFIER = UUID.nameUUIDFromBytes("hollowengine:speed_modifier".toByteArray())