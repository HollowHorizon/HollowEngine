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

    class Blit(minecraft: Minecraft) : RenderTickEvent(minecraft) {
        companion object : EventHandler<Blit>()
    }
}