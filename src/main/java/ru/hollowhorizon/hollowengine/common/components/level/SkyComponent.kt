package ru.hollowhorizon.hollowengine.common.components.level

import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.events.SubscribeEvent
import ru.hollowhorizon.hollowengine.common.events.client.render.SkyRenderEvent

class SkyComponent(level: Level): Component<Level>(level) {
    var skyColor by property("sky_color") { 0x87CEEB  }
    var sunSize by property("sun_size") { 30 }
    var moonSize by property("moon_size") { 20 }

    @SubscribeEvent
    fun onSunRender(event: SkyRenderEvent.SunSize) {
        event.sunSize = sunSize.toFloat()
    }

    @SubscribeEvent
    fun onMoonRender(event: SkyRenderEvent.MoonSize) {
        event.moonSize = moonSize.toFloat()
    }
}