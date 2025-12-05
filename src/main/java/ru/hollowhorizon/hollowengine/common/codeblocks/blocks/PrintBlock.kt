package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.AnyType
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock

class PrintBlock(var defaultMessage: String = "") : CodeBlock(MdColor.Companion.DEEP_PURPLE) {
    override suspend fun execute(context: BlockContext): Any? {
        val msg = inputs["msg"]?.execute(context) ?: defaultMessage
        HollowEngine.LOGGER.info(msg)
        return next?.execute(context)
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Print") { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center) }
        Box(Grow.Companion.Std) {}
        InputSlot("msg", AnyType)
    }
}