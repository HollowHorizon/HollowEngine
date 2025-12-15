package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:string_type")
class StringValueBlock(var value: String) : ExpressionBlock() {
    @Transient
    override val expressionType = typeOf<String>()

    override suspend fun BlockContext.execute() = value
    override fun InputSlotScope.composeContent() {
        TextField(value) {
            modifier.onChange { value = it; notifyChanged() }
                .hint("Значение").font(font)
                .colors(lineColor = Color.Companion.WHITE, textColor = Color.Companion.WHITE)
        }
    }
}