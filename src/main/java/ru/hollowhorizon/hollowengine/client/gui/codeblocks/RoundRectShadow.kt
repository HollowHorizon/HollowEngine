package ru.hollowhorizon.hollowengine.client.gui.codeblocks

import de.fabmax.kool.math.Vec4f
import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.modules.ui2.Shadow
import de.fabmax.kool.modules.ui2.UiNode
import de.fabmax.kool.util.Color

private fun Vec4f.inflate(amount: Float): Vec4f {
    return Vec4f(x - amount, y - amount, z + amount, w + amount)
}

class RoundRectShadow(
    shadowColor: Color,
    val cornerRadius: Dp,
    blurRadius: Dp,
    spread: Dp = Dp.ZERO
) : Shadow(shadowColor, blurRadius, spread) {

    override fun renderUi(node: UiNode) {
        node.apply {
            val blur = blurRadius.px
            val sprd = spread.px
            val cr = cornerRadius.px

            val x = leftPx - sprd
            val y = topPx - sprd
            val w = widthPx + sprd * 2
            val h = heightPx + sprd * 2

            val effCorner = cr + sprd
            val shadowClip = clipBoundsPx.inflate(blur)

            node.getUiPrimitives()
                .roundRectShadow(x, y, w, h, effCorner, blur, shadowClip, shadowColor, inset = false)
        }
    }
}