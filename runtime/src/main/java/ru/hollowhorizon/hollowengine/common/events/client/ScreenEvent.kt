package ru.hollowhorizon.hollowengine.common.events.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hollowengine.common.events.Cancellable
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

open class ScreenEvent(var screen: Screen): ClientEvent {
    class Open(screen: Screen): ScreenEvent(screen) {
        companion object: EventHandler<Open>()
    }
    open class Render(screen: Screen): ScreenEvent(screen) {
        class Pre(screen: Screen, val guiGraphics: GuiGraphics, val mouseX: Int, val mouseY: Int, val partialTick: Float): Render(screen), Cancellable {
            companion object: EventHandler<Pre>()
            override var isCanceled = false
        }
        class Post(screen: Screen, val guiGraphics: GuiGraphics, val mouseX: Int, val mouseY: Int, val partialTick: Float): Render(screen) {
            companion object: EventHandler<Post>()
        }
    }
    class Close(screen: Screen): ScreenEvent(screen) {
        companion object: EventHandler<Close>()
    }
}