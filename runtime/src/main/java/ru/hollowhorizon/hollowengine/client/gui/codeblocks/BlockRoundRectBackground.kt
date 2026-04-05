package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.modules.ui2.UiRenderer
import de.fabmax.kool.modules.ui2.UiSurface
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme
import kotlin.math.max
import kotlin.math.min

class BlockRoundRectBackground(val backgroundColor: Color, val cornerRadius: Dp, val border: Dp, val isExpanded: Boolean) :
    UiRenderer<UiNode> {
    override fun renderUi(node: UiNode) {
        node.apply {
            val c = cornerRadius.px
            val lt = max(leftPx, clipLeftPx - c)
            val rt = min(rightPx, clipRightPx + c)
            val tp = max(topPx, clipTopPx - c)
            val bt = min(bottomPx, clipBottomPx + c)

            node.getUiPrimitives(UiSurface.Companion.LAYER_BACKGROUND).apply {
                if(isExpanded) rect(
                    lt, tp + (bt - tp) / 2f,
                    rt-lt, (bt + tp) / 2f,
                    clipBoundsPx, backgroundColor.mix(Color.Companion.BLACK, 0.5f).mix(ColorTheme.UI.BackgroundSecondary, 0.5f)
                )
                roundRect(
                    lt,
                    tp,
                    rt - lt,
                    bt - tp,
                    c,
                    clipBoundsPx,
                    backgroundColor.mix(Color.Companion.WHITE, 0.33f).mix(ColorTheme.UI.BackgroundSecondary, 0.75f)
                )
                roundRect(lt, tp, border.px, bt - tp, c, clipBoundsPx, backgroundColor)
            }
        }
    }
}