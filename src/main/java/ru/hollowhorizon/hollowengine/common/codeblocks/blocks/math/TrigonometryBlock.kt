package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.math

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import kotlin.math.*

enum class TrigOp(val symbol: String) {
    SIN("sin"), COS("cos"), TAN("tan"),
    ASIN("asin"), ACOS("acos"), ATAN("atan");
}

@Serializable
@SerialName("hollowengine:math/trig_op")
class TrigonometryBlock(var op: TrigOp = TrigOp.SIN) : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<Number>()

    val angle by input<Number>("angle")

    override suspend fun BlockContext.execute(): Any? {
        val value = angle().toDouble()
        return when (op) {
            TrigOp.SIN -> sin(value)
            TrigOp.COS -> cos(value)
            TrigOp.TAN -> tan(value)
            TrigOp.ASIN -> asin(value)
            TrigOp.ACOS -> acos(value)
            TrigOp.ATAN -> atan(value)
        }
    }

    override fun InputSlotScope.composeContent() {
        Box {
            modifier
                .margin(horizontal = 4.dp)
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .background(RoundRectBackground(Color.BLACK.withAlpha(0.3f), sizes.largeGap))
                .onClick {
                    if (it.pointer.isLeftButtonClicked) {
                        val values = TrigOp.entries
                        op = values[(op.ordinal + 1) % values.size]
                    }
                    surface.triggerUpdate()
                    notifyChanged()
                }
                .zLayer(UiSurface.LAYER_DEFAULT)

            Text(op.symbol) { modifier.textColor(Color.WHITE).align(AlignmentX.Center).bold() }
        }

        InputSlot(angle)
    }
}