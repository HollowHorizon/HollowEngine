package ru.hollowhorizon.hollowengine.common.docs

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler

class DocsMain: Screen(Component.empty()) {
  override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
    super.render(guiGraphics, mouseX, mouseY, partialTick)

    ImGuiHandler.drawFrame {

    }
  }
}