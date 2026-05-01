package ru.hollowhorizon.hollowengine.common.events.client.render

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.common.events.ClientEvent

open class RenderTickEvent(val minecraft: Minecraft): ClientEvent {
    class Pre(minecraft: Minecraft) : RenderTickEvent(minecraft)
    class Post(minecraft: Minecraft) : RenderTickEvent(minecraft)
    class Blit(minecraft: Minecraft) : RenderTickEvent(minecraft)
}