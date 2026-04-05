package ru.hollowhorizon.hollowengine.common.codeblocks.blocks

import de.fabmax.kool.modules.ui2.AlignmentY
import de.fabmax.kool.modules.ui2.Text
import de.fabmax.kool.modules.ui2.alignY
import de.fabmax.kool.modules.ui2.textColor
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.codeblocks.InputSlotScope

fun InputSlotScope.DefaultText(text: String) {
    Text(text) {
        modifier.textColor(Color.WHITE).alignY(AlignmentY.Center).bold()
    }
}