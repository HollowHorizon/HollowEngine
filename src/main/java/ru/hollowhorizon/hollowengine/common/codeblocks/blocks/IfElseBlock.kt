package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ContainerBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ExpressionTypes

@Serializable
class IfElseBlock : CodeBlock(), ContainerBlock {
    override suspend fun execute(context: BlockContext): Any? {
        val condition = inputs["cond"]?.execute(context) as? Boolean ?: false
        if (condition) {
            inputs["then"]?.execute(context)
        } else {
            inputs["else"]?.execute(context)
        }
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Если") { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center) }
        Box(Grow.Companion.Std) {}
        InputSlot("cond", ExpressionTypes.BOOLEAN)
    }

    override fun BlockEditor.InputSlotScope.composeBody() {
        BodySlot("then")

        SectionSeparator("Иначе")

        BodySlot("else")
    }
}

@Serializable
class IfBlock : CodeBlock(), ContainerBlock {
    override suspend fun execute(context: BlockContext): Any? {
        val condition = inputs["cond"]?.execute(context) as? Boolean ?: false
        if (condition) {
            inputs["then"]?.execute(context)
        }
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Если") { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center) }
        Box(Grow.Companion.Std) {}
        InputSlot("cond", ExpressionTypes.BOOLEAN)
    }

    override fun BlockEditor.InputSlotScope.composeBody() {
        BodySlot("then")
    }
}
