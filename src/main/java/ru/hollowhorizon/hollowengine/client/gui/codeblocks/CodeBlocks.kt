package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color


fun UiScope.BlockLabel(text: String) {
    Text(text) {
        modifier
            .textColor(Color.WHITE)
            .font(sizes.normalText.derive(14f))
            .alignY(AlignmentY.Center)
    }
}

fun UiScope.BlockInput(
    value: String,
    onValueChange: (String) -> Unit,
    body: UiModifier.() -> Unit = {},
) {
    TextField(value) {
        body(modifier)
        this.modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .width(Grow(1f, min = 30.dp, max = FitContent))
            .background(RoundRectBackground(Color.WHITE, sizes.gap))
            .alignY(AlignmentY.Center)
            .textAlignX(AlignmentX.Center)
            .onChange { onValueChange(it) }
    }
}

