package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.utils.lang

fun UiScope.ServerIdeWarning() {
    modifier.backgroundColor(Color.BLACK.withAlpha(0.33f))
        .layout(CellLayout)

    Box {
        modifier.border(RectBorder(Color.WHITE, sizes.borderWidth))
            .backgroundColor(Color.BLACK.withAlpha(0.65f))
            .padding(sizes.smallGap)
            .align(AlignmentX.Center, AlignmentY.Center)

        Text("hollowengine.gui.server_ide_warning.message".lang) {
            modifier.isWrapText(true).width(Grow.Std)
        }
    }
}