package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionType
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:strings/concat")
class StringConcatBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.TYPES

    @Transient
    override val expressionType: ExpressionType = typeOf<String>()

    val parts by inputList<Any>("parts")

    override suspend fun execute(): Any {
        return buildString {
            parts().forEach { append(it?.toString().orEmpty()) }
        }
    }

    override fun InputSlotScope.composeContent() {
        Text("concat") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlotList("parts", AnyType)
    }
}

@Serializable
@SerialName("hollowengine:strings/to_string")
class ToStringBlock : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.TYPES

    @Transient
    override val expressionType: ExpressionType = typeOf<String>()

    val value by input<Any>("value")

    override suspend fun execute(): Any {
        return value()?.toString().orEmpty()
    }

    override fun InputSlotScope.composeContent() {
        Text("to string") { modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(value)
    }
}
