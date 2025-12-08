package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.BlockEditor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockContext
import ru.hollowhorizon.hollowengine.common.codeblocks.CodeBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.ContainerBlock

@Serializable
@SerialName("hollowengine:loops/repeat")
class RepeatBlock : CodeBlock(), ContainerBlock {
    val times by input<Int>("times")
    val body by input<Unit>("body")

    override suspend fun BlockContext.execute() {
        // TODO: Научить эту штуку считать с учётом сохранения
        repeat(times()) {
            body()
        }
    }

    override fun BlockEditor.InputSlotScope.composeContent() {
        Text("Повторить") { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center).bold() }
        InputSlot(times)
        Text("Раз") { modifier.textColor(Color.Companion.WHITE).alignY(AlignmentY.Center).bold() }
    }

    override fun BlockEditor.InputSlotScope.composeBody() {
        BodySlot("body")
    }
}