package ru.hollowhorizon.hollowengine.client.gui.kool

import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.Dimensions

val Sizes.lineHeight: Dp get() = baseSize * (2f/3f)
val Sizes.baseSize: Dp get() = largeGap * 2f

val Colors.hoverBg: Color get() = secondaryVariantAlpha(0.35f)
val Colors.backgroundMid: Color get() = background.mix(backgroundVariant, 0.5f)
val Colors.weakDividerColor: Color get() = secondaryVariantAlpha(0.75f)

fun UiScope.menuDivider(
    marginStart: Dp = Dimensions.PaddingMedium,
    marginEnd: Dp = Dimensions.PaddingMedium,
    marginTop: Dp = Dimensions.PaddingSmall,
    marginBottom: Dp = Dimensions.PaddingSmall,
    color: Color = colors.weakDividerColor
) {
    Box(Grow.Std, Dimensions.PaddingSmall) {
        modifier
            .backgroundColor(color)
            .margin(start = marginStart, end = marginEnd, top = marginTop, bottom = marginBottom)
    }
}