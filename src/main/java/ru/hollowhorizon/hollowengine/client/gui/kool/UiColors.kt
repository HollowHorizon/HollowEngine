package ru.hollowhorizon.hollowengine.client.gui.kool

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor

val Sizes.lineHeight: Dp get() = baseSize * (2f/3f)
val Sizes.baseSize: Dp get() = largeGap * 2f
val Sizes.lineHeightLarge: Dp get() = baseSize * 0.9f
val Sizes.heightTitleBar: Dp get() = lineHeightLarge
val Sizes.heightWindowTitleBar: Dp get() = heightTitleBar * 1.1f
val Sizes.scrollbarWidth: Dp get() = gap * 0.33f

val Sizes.editorPanelMarginStart: Dp get() = gap * 1.5f
val Sizes.editorPanelMarginEnd: Dp get() = gap

val Colors.hoverBg: Color get() = secondaryVariantAlpha(0.35f)
val Colors.backgroundMid: Color get() = background.mix(backgroundVariant, 0.5f)
val Colors.weakDividerColor: Color get() = secondaryVariantAlpha(0.75f)

object UiColors {
    val border = Color("0f1114ff")
    val titleBg = Color("343a49ff")
    val windowTitleBgAccent = MdColor.DEEP_PURPLE
    val titleBgAccent = MdColor.DEEP_PURPLE
    val titleText = Color("dbe6ffff")
    val secondaryBright = Color("a0b3d8ff")
    val selectionChild = Color("ff7b0080")
}

fun ColumnScope.menuDivider(
    marginStart: Dp = sizes.editorPanelMarginStart,
    marginEnd: Dp = sizes.editorPanelMarginEnd,
    marginTop: Dp = sizes.smallGap,
    marginBottom: Dp = Dp.ZERO,
    color: Color = colors.weakDividerColor
) {
    Box(Grow.Std, sizes.borderWidth) {
        modifier
            .backgroundColor(color)
            .margin(start = marginStart, end = marginEnd, top = marginTop, bottom = marginBottom)
    }
}