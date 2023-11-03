package ru.hollowhorizon.hollowengine.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.client.event.RenderGuiOverlayEvent
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.loading.moddiscovery.ModInfo
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
    private val customTooltips = HashMap<Item, MutableList<Component>>()
    private val customModNames = HashMap<String, String>()

    private fun keyBindName(name: String) = "key.${HollowEngine.MODID}.$name"

    fun addTooltip(item: Item, tooltip: Component) {
        customTooltips.computeIfAbsent(item) { ArrayList() }.add(tooltip)
    }

    fun setModName(modid: String, new: String) {
        val optionalMod = ModList.get().getModContainerById(modid)

        if(!optionalMod.isPresent) return

        val mod = optionalMod.get().modInfo

        customModNames[modid] = mod.displayName

        val displayNameSetter = ModInfo::class.java.getDeclaredField("displayName")

        displayNameSetter.isAccessible = true
        displayNameSetter.set(mod, new)
    }

    fun resetClientScripting() {
        customTooltips.clear()
        customModNames.forEach { (modid, original) ->
            val mod = ModList.get().getModContainerById(modid).get().modInfo

            val displayNameSetter = ModInfo::class.java.getDeclaredField("displayName")

            displayNameSetter.isAccessible = true
            displayNameSetter.set(mod, original)
        }
    }

    @JvmStatic
    fun renderOverlay(event: RenderGuiOverlayEvent.Post) {
        val gui = Minecraft.getInstance().gui

        val window = event.window
        if (event.overlay == VanillaGuiOverlay.HOTBAR.type()) {
            MouseDriver.draw(
                gui,
                event.poseStack,
                window.guiScaledWidth / 2,
                window.guiScaledHeight / 2 + 16,
                event.partialTick
            )
        }

    }

    @JvmStatic
    fun onTooltipRender(event: ItemTooltipEvent) {
        val item = event.itemStack.item

        if (item in customTooltips) event.toolTip.addAll(customTooltips[item] ?: emptyList())
    }

    @JvmStatic
    fun onClicked(event: InputEvent.MouseButton.Pre) {
        if (event.action != 1) return

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
            Minecraft.getInstance().setScreen(ProgressManagerScreen())
        }
    }

    fun initKeys() {
        MOD_BUS.addListener { event: RegisterKeyMappingsEvent ->
            event.register(OPEN_EVENT_LIST)
        }
    }
}