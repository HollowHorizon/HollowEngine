package ru.hollowhorizon.hollowengine.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.client.event.RenderGuiOverlayEvent
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.screen.MouseDriver
import ru.hollowhorizon.hollowengine.client.screen.ProgressManagerScreen
import ru.hollowhorizon.hollowengine.common.capabilities.storyTeam
import ru.hollowhorizon.hollowengine.common.network.Container
import ru.hollowhorizon.hollowengine.common.network.MouseButton
import ru.hollowhorizon.hollowengine.common.network.MouseClickedPacket

object ClientEvents {
    const val HS_CATEGORY = "key.categories.mod.hollowengine"
    val OPEN_EVENT_LIST = KeyMapping(keyBindName("event_list"), GLFW.GLFW_KEY_GRAVE_ACCENT, HS_CATEGORY)
    val canceledButtons = hashSetOf<MouseButton>()

    private fun keyBindName(name: String): String {
        return java.lang.String.format("key.%s.%s", HollowEngine.MODID, name)
    }

    @JvmStatic
    fun renderOverlay(event: RenderGuiOverlayEvent.Pre) {
        if (event.overlay == VanillaGuiOverlay.CROSSHAIR.type()) {
            val window = event.window
            MouseDriver.draw(event.poseStack, window.width / 2, window.height / 2 + 16, event.partialTick)
        }
    }

    @JvmStatic
    fun onClicked(event: InputEvent.MouseButton.Pre) {
        val button = MouseButton.from(event.button)
        if (canceledButtons.isNotEmpty()) MouseClickedPacket().send(Container(button))
        if (canceledButtons.removeIf { it.ordinal == button.ordinal }) event.isCanceled = true
    }

    @JvmStatic
    fun onKeyPressed(event: InputEvent.Key) {
        if (OPEN_EVENT_LIST.isActiveAndMatches(
                InputConstants.getKey(
                    event.key,
                    event.scanCode
                )
            ) && Minecraft.getInstance().screen == null
        ) {
            val manager = Minecraft.getInstance().player?.storyTeam()?.progressManager

            if (manager != null) Minecraft.getInstance().setScreen(ProgressManagerScreen(manager))
        }
    }

    fun initKeys() {
        FMLJavaModLoadingContext.get().modEventBus.addListener { event: RegisterKeyMappingsEvent ->
            event.register(OPEN_EVENT_LIST)
        }
    }
}