package ru.hollowhorizon.hollowengine.common.events.client.render

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.common.events.ClientEvent
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler

open class RenderTickEvent(val minecraft: Minecraft) : ClientEvent {
    class Pre(minecraft: Minecraft) : RenderTickEvent(minecraft) {
        companion object : EventHandler<Pre>()
    }

    class Post(minecraft: Minecraft) : RenderTickEvent(minecraft) {
        companion object : EventHandler<Post>()
    }

    /** The level is in the main target, finished, and the GUI has not been drawn over it yet. */
    class LevelRendered(minecraft: Minecraft) : RenderTickEvent(minecraft) {
        companion object : EventHandler<LevelRendered>()
    }

    class Blit(minecraft: Minecraft) : RenderTickEvent(minecraft) {
        companion object : EventHandler<Blit>()
    }
}