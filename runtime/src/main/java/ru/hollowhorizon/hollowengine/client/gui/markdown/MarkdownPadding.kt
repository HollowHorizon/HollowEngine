package ru.hollowhorizon.hollowengine.client.gui.markdown

import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.UiModifier
import de.fabmax.kool.modules.ui2.padding

fun PaddingValues(all: Dp) = PaddingValues(all, all, all, all)
fun PaddingValues(horizontal: Dp = Dp(0f), vertical: Dp = Dp(0f)) =
    PaddingValues(horizontal, vertical, horizontal, vertical)

data class PaddingValues(val left: Dp, val top: Dp, val right: Dp, val bottom: Dp)

fun <T: UiModifier> T.padding(p: PaddingValues): T {
    return padding(p.left, p.top, p.right, p.bottom)
}

object MarkdownPadding {
    val block: Dp = Dp(2f)
    val list: Dp = Dp(4f)
    val listItemTop: Dp = Dp(4f)
    val listItemBottom: Dp = Dp(4f)
    val listIndent: Dp = Dp(8f)
    val codeBlock: PaddingValues = PaddingValues(Dp(8f))
    val blockQuote: PaddingValues = PaddingValues(horizontal = Dp(16f))
    val blockQuoteText: PaddingValues = PaddingValues(vertical = Dp(4f))
    val blockQuoteBar: PaddingValues = PaddingValues(left = Dp(4f), top = Dp(2f), right = Dp(4f), bottom = Dp(2f))
}

object MarkdownDimens {
    val dividerThickness: Dp = Dp(1f)
    val codeBackgroundCornerSize: Dp = Dp(8f)
    val blockQuoteThickness: Dp = Dp(2f)
    val tableMaxWidth: Dp = Dp.UNBOUNDED
    val tableCellWidth: Dp = Dp(160f)
    val tableCellPadding: Dp = Dp(16f)
    val tableCornerSize: Dp = Dp(8f)
}