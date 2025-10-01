package ru.hollowhorizon.hollowengine.common.components.level

import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.api.Init
import ru.hollowhorizon.hollowengine.common.components.Component
import ru.hollowhorizon.hollowengine.common.components.annotations.ComponentMeta
import ru.hollowhorizon.hollowengine.common.components.system.Cardinal
import ru.hollowhorizon.hollowengine.common.events.client.render.SkyRenderEvent

@ComponentMeta("hollowengine:sky_renderer")
class SkyComponent: Component<Level>() {
    var skyColor by property("sky_color") { 0x87CEEB  }
    var sunSize by property("sun_size") { 30 }
    var moonSize by property("moon_size") { 20 }
}

@Init
fun loadComponents() {
    Cardinal.on<SkyRenderEvent.SunSize, SkyComponent> {
        sunSize = it.sunSize.toFloat()
    }
    Cardinal.on<SkyRenderEvent.MoonSize, SkyComponent> {
        moonSize = it.moonSize.toFloat()
    }
}

