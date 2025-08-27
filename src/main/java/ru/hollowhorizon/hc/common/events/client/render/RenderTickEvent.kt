package ru.hollowhorizon.hc.common.events.client.render

import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.common.events.Event

open class RenderTickEvent(val minecraft: Minecraft): Event {
    class Pre(minecraft: Minecraft) : RenderTickEvent(minecraft)
    class Post(minecraft: Minecraft) : RenderTickEvent(minecraft)
}