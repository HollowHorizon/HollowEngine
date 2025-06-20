package ru.hollowhorizon.hollowengine.client.gui

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.Box
import de.fabmax.kool.modules.ui2.UiScope
import de.fabmax.kool.modules.ui2.tint
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hc.client.kool.minecraft.Image
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverListener

fun UiScope.IconButton(image: String, hovered: String, body: UiScope.() -> Unit) = Box {
    val (isHovered, anim) = hoverListener()

    var factor = Easing.quadRev(anim.progressAndUse())
    if (!isHovered.use()) factor = 1f - factor

    body()

    Image(image) {
        modifier.tint(Color(1f, 1f, 1f, factor))
    }
    Image(hovered) {
        modifier.tint(Color(1f, 1f, 1f, 1f - factor))
    }
}