package ru.hollowhorizon.hollowengine.client.keys

import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.registry.RegisterKeyBindingsEvent
import ru.hollowhorizon.hc.common.events.tick.TickEvent
import ru.hollowhorizon.hc.common.network.request
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.gui.DashBoardScreen
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.RequestTreePacket

val HOLLOW_ENGINE_KEY = KeyMapping("key.hollowengine.menu", GLFW.GLFW_KEY_F12, "key.hollowengine")

@SubscribeEvent
fun onRegisterKeys(event: RegisterKeyBindingsEvent) {
    event.registerKeyMapping(HOLLOW_ENGINE_KEY)
}

@SubscribeEvent
fun onTick(event: TickEvent.Client) {
    if(HOLLOW_ENGINE_KEY.isDown) {
        scopeSync {
            val newTree = RequestTreePacket().request().tree
            RenderSystem.recordRenderCall {
                IDEGuiV2.fileTree = newTree
                IDEGuiV2.open()
            }
        }
    }
}