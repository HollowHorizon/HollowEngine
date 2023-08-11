package ru.hollowhorizon.hollowengine.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.Input
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.screen.ProgressManagerScreen
import ru.hollowhorizon.hollowengine.common.capabilities.storyTeam

object ClientEvents {
    const val HS_CATEGORY = "key.categories.mod.hollowengine"
    val OPEN_EVENT_LIST = KeyMapping(keyBindName("event_list"), GLFW.GLFW_KEY_GRAVE_ACCENT, HS_CATEGORY)

    private fun keyBindName(name: String): String {
        return java.lang.String.format("key.%s.%s", HollowEngine.MODID, name)
    }

    @JvmStatic
    fun renderLast(event: RenderLevelStageEvent) {
    }

    @JvmStatic
    fun onKeyPressed(event: InputEvent.Key) {
        if(OPEN_EVENT_LIST.isActiveAndMatches(InputConstants.getKey(event.key, event.scanCode)) && Minecraft.getInstance().screen == null) {
            val manager = Minecraft.getInstance().player?.storyTeam()?.progressManager

            if(manager!=null) Minecraft.getInstance().setScreen(ProgressManagerScreen(manager))
        }
    }

    fun initKeys() {
        FMLJavaModLoadingContext.get().modEventBus.addListener { event: RegisterKeyMappingsEvent ->
            event.register(OPEN_EVENT_LIST)
        }
    }
}