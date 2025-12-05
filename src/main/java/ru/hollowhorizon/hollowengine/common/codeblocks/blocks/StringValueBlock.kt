package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.TextField
import de.fabmax.kool.modules.ui2.colors
import de.fabmax.kool.modules.ui2.hint
import de.fabmax.kool.modules.ui2.onChange
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionTypes

@Serializable
@SerialName("hollowengine:string_type")
class StringValueBlock(var value: String) : CodeBlock(), ExpressionBlock {
    @Transient
    override val expressionType = ExpressionTypes.STRING

    override suspend fun execute(context: BlockContext) = value
    override fun BlockEditor.InputSlotScope.composeContent() {
        TextField(value) {
            modifier.onChange { value = it; notifyChanged() }
                .hint("Значение")
                .colors(lineColor = Color.Companion.WHITE, textColor = Color.Companion.WHITE)
        }
    }
}