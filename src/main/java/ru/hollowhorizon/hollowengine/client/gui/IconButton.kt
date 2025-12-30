package ru.hollowhorizon.hollowengine.client.gui

import de.fabmax.kool.math.Easing
import de.fabmax.kool.modules.ui2.*
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.scripting.tools.hoverable
import ru.hollowhorizon.hollowengine.client.kool.minecraft.Image

fun UiScope.IconButton(image: String, hovered: String, body: UiScope.() -> Unit) = Box {
    val isHovered by modifier.hoverable()

    val factor by animateFloatAsState(if (isHovered) 1f else 0f, tween(easing = Easing.easeOutQuart))

    body()  

    Image(image) {
        modifier.tint(Color(1f, 1f, 1f, factor))
    }
    Image(hovered) {
        modifier.tint(Color(1f, 1f, 1f, 1f - factor))
    }
}