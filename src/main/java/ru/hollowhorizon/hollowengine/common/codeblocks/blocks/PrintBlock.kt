package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock

@Serializable
@SerialName("hollowengine:print")
class PrintBlock : CodeBlock() {
    val msg by input<Any>("msg")

    override suspend fun BlockContext.execute() {
        HollowEngine.LOGGER.info(msg())
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Напечатать") { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center).bold() }
        Box(Grow.Companion.Std) {}
        InputSlot(msg)
    }
}