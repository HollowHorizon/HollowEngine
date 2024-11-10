package ru.hollowhorizon.hollowengine.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hc.common.coroutines.scopeSync
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hc.common.network.request
import ru.hollowhorizon.hollowengine.client.docs.DocsRenderer
import ru.hollowhorizon.hollowengine.client.gui.scripting.IDEGuiV2
import ru.hollowhorizon.hollowengine.client.gui.scripting.RequestTreePacket

object DashBoardScreen : ImGuiScreen() {
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
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 15f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 3f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowTitleAlign, 0.5f, 0.5f)
        ImGui.setNextWindowPos((window.width - width) / 2f, (window.height - height) / 2f)
        ImGui.setNextWindowSize(width, height)
        centredWindow("HollowEngine Меню", args = ImGuiWindowFlags.NoCollapse) {
            val size =
                (ImGui.getContentRegionMax().x - ImGui.getStyle().itemSpacingX * 4) / 4 - ImGui.getStyle().framePaddingX * 2

            modTabs.forEachIndexed { index, npcOption ->
                // Не знаю почему, но эти кнопки иногда срабатывают при первом открытии окна...
                // Как вариант дождёмся пока закончится анимация
                if (imageButton(npcOption.name, size) && ImGui.getStyle().alpha > 0.5f) {
                    npcOption.onClick()
                }
                if ((index + 1) % 4 != 0) ImGui.sameLine()
            }
        }

        ImGui.popStyleVar(3)
    }

    fun imageButton(image: String, size: Float): Boolean {
        ImGui.pushID(image)
        val isClicked =
            ImGui.imageButton("hollowengine:textures/gui/icons/$image.png".rl.toTexture().id.toLong(), size, size)
        ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 3f)
        if (ImGui.isItemHovered()) ImGui.setTooltip(
            //? if >1.20.1 {
            /*Language.getInstance().getOrDefault("mod_tabs.$image", "No description available.")
            *///?} else {
            Language.getInstance().getOrDefault("mod_tabs.$image")
            //?}
        )
        ImGui.popStyleVar()
        ImGui.popID()
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
fun onAddTab(event: DashBoardScreen.TabEvent) {
    event.register(DashBoardScreen.Tab("code_editor") {
        scopeSync {
            val newTree = RequestTreePacket().request().tree
            RenderSystem.recordRenderCall {
                IDEGuiV2.fileTree = newTree
                IDEGuiV2.open()
            }
        }
    })
    event.register(DashBoardScreen.Tab("docs", DocsRenderer()::open))
}