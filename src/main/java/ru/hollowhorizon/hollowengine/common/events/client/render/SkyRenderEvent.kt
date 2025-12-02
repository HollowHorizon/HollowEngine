package ru.hollowhorizon.hollowengine.common.events.client.render

import net.minecraft.client.multiplayer.ClientLevel
import ru.hollowhorizon.hollowengine.common.components.ComponentDispatcher
import ru.hollowhorizon.hollowengine.common.events.ComponentDispatcherEvent

open class SkyRenderEvent(val level: ClientLevel) : ComponentDispatcherEvent {
    override val owner = level as ComponentDispatcher

    class SunSize(level: ClientLevel, var sunSize: Float) : SkyRenderEvent(level)
    class MoonSize(level: ClientLevel, var moonSize: Float) : SkyRenderEvent(level)
}