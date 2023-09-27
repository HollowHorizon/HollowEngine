package ru.hollowhorizon.hollowengine.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraftforge.client.ClientRegistry
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.client.gui.ForgeIngameGui
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hc.common.network.send
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.client.screen.MouseDriver
import ru.hollowhorizon.hollowengine.client.screen.ProgressManagerScreen
import ru.hollowhorizon.hollowengine.common.network.Container
import ru.hollowhorizon.hollowengine.common.network.MouseButton
import ru.hollowhorizon.hollowengine.common.network.MouseClickedPacket
import thedarkcolour.kotlinforforge.forge.MOD_BUS

object ClientEvents {
    const val HS_CATEGORY = "key.categories.mod.hollowengine"
    val OPEN_EVENT_LIST = KeyMapping(keyBindName("event_list"), GLFW.GLFW_KEY_GRAVE_ACCENT, HS_CATEGORY)
    val canceledButtons = hashSetOf<MouseButton>()

    private fun keyBindName(name: String): String {
        return "key.${HollowEngine.MODID}.$name"
    }

    @JvmStatic
    fun renderOverlay(event: RenderGameOverlayEvent.Post) {
        val gui = Minecraft.getInstance().gui

        val window = event.window
        if(event.type == ForgeIngameGui.HOTBAR_ELEMENT) {
            MouseDriver.draw(
                gui,
                event.matrixStack,
                window.guiScaledWidth / 2,
                window.guiScaledHeight / 2 + 16,
                event.partialTicks
            )
        }

    }

    @JvmStatic
    fun onClicked(event: InputEvent.MouseInputEvent) {
        if(event.action != 1) return

        val button = MouseButton.from(event.button)
        if (canceledButtons.isNotEmpty()) MouseClickedPacket().send(Container(button))
        if (canceledButtons.removeIf { it.ordinal == button.ordinal }) event.isCanceled = true
    }

    @JvmStatic
    fun onKeyPressed(event: InputEvent.KeyInputEvent) {
        if (OPEN_EVENT_LIST.isActiveAndMatches(
                InputConstants.getKey(
                    event.key,
                    event.scanCode
                )
            ) && Minecraft.getInstance().screen == null
        ) {
            Minecraft.getInstance().setScreen(ProgressManagerScreen())
        }
    }

    fun initKeys() {
        ClientRegistry.registerKeyBinding(OPEN_EVENT_LIST)
    }
}