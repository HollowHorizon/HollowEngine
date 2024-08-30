package ru.hollowhorizon.hollowengine.client.gui

import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hollowengine.client.docs.DocsRenderer
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGui

object HollowEngineGui : ImGuiScreen() {
    val modTabs = ArrayList<Tab>()

    override fun init() {
        super.init()

        modTabs.clear()

        TabEvent(modTabs::add).post()
    }

    override fun Graphics.draw() {
        val window = Minecraft.getInstance().window
        val width = window.width * 0.9f
        val height = window.height * 0.9f
        ImGui.setNextWindowSize(width, height)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 15f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 3f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowTitleAlign, 0.5f, 0.5f)
        centredWindow(
            "Меню HollowEngine",
            ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.AlwaysAutoResize
        ) {
            val size =
                (ImGui.getContentRegionMax().x - ImGui.getStyle().itemSpacingX * 4) / 4 - ImGui.getStyle().framePaddingX * 2

            modTabs.forEachIndexed { index, npcOption ->
                if (imageButton(npcOption.name, size)) npcOption.onClick()
                if ((index + 1) % 4 != 0) ImGui.sameLine()
            }
        }
        ImGui.popStyleVar(3)
    }

    fun imageButton(image: String, size: Float): Boolean {
        val isClicked = ImGui.imageButton("hollowengine:textures/gui/icons/$image.png".rl.toTexture().id, size, size)
        ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 3f)
        if (ImGui.isItemHovered()) ImGui.setTooltip(
            //? if >1.20.1 {
            Language.getInstance().getOrDefault("mod_tabs.$image", "No description available.")
            //?} else {
            /*Language.getInstance().getOrDefault("mod_tabs.$image")
            *///?}
        )
        ImGui.popStyleVar()
        return isClicked
    }

    class Tab(val name: String, val onClick: () -> Unit)
    class TabEvent(private val generator: (Tab) -> Unit) : Event {
        fun register(tab: Tab) {
            generator(tab)
        }
    }
}

@SubscribeEvent
fun onAddTab(event: HollowEngineGui.TabEvent) {
    event.register(HollowEngineGui.Tab("code_editor", IDEGui::open))
    event.register(HollowEngineGui.Tab("docs", DocsRenderer()::open))
}