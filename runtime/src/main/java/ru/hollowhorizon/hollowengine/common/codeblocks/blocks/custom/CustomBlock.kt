package ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope
import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlocksColors
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.DefaultText
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ContainerBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.EndBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StartBlock

@Serializable
@SerialName("hollowengine:custom/custom_block")
class CustomBlock(var function: String = ""): StartBlock(), ContainerBlock, EndBlock {
    override val color: Color get() = CodeBlocksColors.FUNCTIONS

    val body by input<Unit>("body")

    override suspend fun trigger() {
        throw IllegalAccessException("Custom blocks not allowed to start like triggers")
    }

    override suspend fun execute() {
        body()
    }

    override fun InputSlotScope.composeContent() {
        DefaultText("hollowengine.gui.codeblocks.label.function".lang)
        TextField(function) {
            modifier.width(FitContent).margin(start = 5.dp)
                .alignY(AlignmentY.Center)
                .onChange { function = it }
                .hint("hollowengine.gui.codeblocks.hint.function_name".lang).font(font)
                .colors(textColor = Color.WHITE, lineColor = Color.WHITE)
        }
    }

    override fun InputSlotScope.composeBody() {
        BodySlot("body")
    }
}