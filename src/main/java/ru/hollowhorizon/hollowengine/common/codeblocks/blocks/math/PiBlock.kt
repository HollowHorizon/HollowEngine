package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.math


import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf
import kotlin.math.E
import kotlin.math.PI

@Serializable
@SerialName("hollowengine:math/pi_const")
class PiBlock : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<Number>()

    override suspend fun execute() = PI

    override fun InputSlotScope.composeContent() {
        Text("π") {
            modifier.textColor(Color.WHITE).bold()
        }
    }
}

@Serializable
@SerialName("hollowengine:math/e_const")
class EBlock : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<Number>()

    override suspend fun execute() = E

    override fun InputSlotScope.composeContent() {
        Text("e") {
            modifier.textColor(Color.WHITE).bold()
        }
    }
}