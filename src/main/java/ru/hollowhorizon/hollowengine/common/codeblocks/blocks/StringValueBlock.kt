package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.gui.scripting.EditorTheme
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ExpressionBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.typeOf

@Serializable
@SerialName("hollowengine:string_type")
class StringValueBlock(var value: String) : ExpressionBlock() {
    override val color: Color get() = CodeBlocksColors.TYPES

    @Transient
    override val expressionType = typeOf<String>()

    override suspend fun execute() = value
    override fun InputSlotScope.composeContent() {
        TextField(value) {
            modifier.onChange { value = it; notifyChanged() }
                .hint("Значение").font(font)
                .colors(
                    lineColor = Color.WHITE,
                    textColor = Color.WHITE,
                    selectionColor = EditorTheme.selection,
                    cursorColor = EditorTheme.caret
                )
        }
    }
}