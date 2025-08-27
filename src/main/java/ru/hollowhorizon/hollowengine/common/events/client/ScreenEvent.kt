package ru.hollowhorizon.hollowengine.common.events.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hollowengine.common.events.Cancelable
import ru.hollowhorizon.hollowengine.common.events.Event

open class ScreenEvent(var screen: Screen): Event {
    class Open(screen: Screen): ScreenEvent(screen)
    open class Render(screen: Screen): ScreenEvent(screen) {
        class Pre(screen: Screen, val guiGraphics: GuiGraphics, val mouseX: Int, val mouseY: Int, val partialTick: Float): Render(screen), Cancelable {
            override var isCanceled = false
        }
        class Post(screen: Screen, val guiGraphics: GuiGraphics, val mouseX: Int, val mouseY: Int, val partialTick: Float): Render(screen)
    }
    class Close(screen: Screen): ScreenEvent(screen)
}