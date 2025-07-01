@file:ClientOnly

package ru.hollowhorizon.hollowengine.client.keys

import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.events.ClientOnly
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterKeyBindingsEvent
import ru.hollowhorizon.hc.common.events.tick.TickEvent
import ru.hollowhorizon.hollowengine.client.gui.scripting.ScriptingEnvironmentScreen

val HOLLOW_ENGINE_KEY = KeyMapping("key.hollowengine.menu", GLFW.GLFW_KEY_F12, "key.hollowengine")

@SubscribeEvent
fun onRegisterKeys(event: RegisterKeyBindingsEvent) {
    event.registerKeyMapping(HOLLOW_ENGINE_KEY)
}

@SubscribeEvent
fun onTick(event: TickEvent.Client) {
    if (HOLLOW_ENGINE_KEY.isDown) {
        ScriptingEnvironmentScreen().open()
    }
}