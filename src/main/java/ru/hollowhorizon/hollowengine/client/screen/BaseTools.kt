package ru.hollowhorizon.hollowengine.client.screen

import ru.hollowhorizon.hc.client.screens.HollowScreen
import ru.hollowhorizon.hc.client.screens.widget.layout.BoxBuilder

fun BoxBuilder.defaultSettings() {
    size = 90.pc x 90.pc
    renderer = { stack, x, y, w, h ->
        HollowScreen.fill(stack, x, y, x + w, y + h, 0x8800173D.toInt())
        HollowScreen.fill(stack, x, y, x + w, y + 2, 0xFF07BBDB.toInt())
        HollowScreen.fill(stack, x, y + h - 2, x + w, y + h, 0xFF07BBDB.toInt())
        HollowScreen.fill(stack, x, y, x + 2, y + h, 0xFF07BBDB.toInt())
        HollowScreen.fill(stack, x + w - 2, y, x + w, y + h, 0xFF07BBDB.toInt())
    }
}