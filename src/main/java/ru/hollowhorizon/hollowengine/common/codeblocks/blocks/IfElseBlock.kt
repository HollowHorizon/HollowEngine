package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.model.ContainerBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.model.StatementBlock

@Serializable
@SerialName("hollowengine:control/if-else")
class IfElseBlock : StatementBlock(), ContainerBlock {
    val condition by input<Boolean>("condition")
    val thenBranch by input<Unit>("then")
    val elseBranch by input<Unit>("else")

    override suspend fun BlockContext.execute() {
        if(condition()) {
            thenBranch()
        } else {
            elseBranch()
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Если") { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center).bold() }
        Box(Grow.Companion.Std) {}
        InputSlot(condition)
    }

    override fun BlockEditor.InputSlotScope.composeBody() {
        BodySlot("then")

        SectionSeparator("Иначе")

        BodySlot("else")
    }
}

@Serializable
@SerialName("hollowengine:control/if")
class IfBlock : StatementBlock(), ContainerBlock {
    val condition by input<Boolean>("condition")
    val thenBranch by input<Unit>("then")

    override suspend fun BlockContext.execute() {
        if (condition()) {
            thenBranch()
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Если") { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center).bold() }
        Box(Grow.Companion.Std) {}
        InputSlot(condition)
    }

    override fun BlockEditor.InputSlotScope.composeBody() {
        BodySlot("then")
    }
}
