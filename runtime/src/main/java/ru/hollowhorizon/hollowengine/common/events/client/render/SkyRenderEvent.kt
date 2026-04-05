package ru.hollowhorizon.hollowengine.common.events.client.render

import net.minecraft.client.multiplayer.ClientLevel
import ru.hollowhorizon.hollowengine.common.events.Event

open class SkyRenderEvent(val level: ClientLevel): Event {

    class SunSize(level: ClientLevel, var sunSize: Float) : SkyRenderEvent(level)
    class MoonSize(level: ClientLevel, var moonSize: Float) : SkyRenderEvent(level)
}