package ru.hollowhorizon.hollowengine.client.utils

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.utils.GuiAnimator
import kotlin.reflect.KProperty

operator fun GuiAnimator.getValue(nothing: Any?, property: KProperty<*>): Int {
    update(Minecraft.getInstance().partialTick)
    return value
}
