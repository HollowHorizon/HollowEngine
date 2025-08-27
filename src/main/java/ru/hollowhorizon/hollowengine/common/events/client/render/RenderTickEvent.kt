package ru.hollowhorizon.hollowengine.common.events.client.render

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hollowengine.common.events.Event

open class RenderTickEvent(val minecraft: Minecraft): Event {
    class Pre(minecraft: Minecraft) : RenderTickEvent(minecraft)
    class Post(minecraft: Minecraft) : RenderTickEvent(minecraft)
}