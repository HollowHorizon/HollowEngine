package ru.hollowhorizon.hollowengine.client.gui

//? if >=1.20.1
import com.mojang.blaze3d.Blaze3D
import imgui.flag.ImGuiCol
import imgui.internal.ImGui
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import ru.hollowhorizon.hc.api.HasImGuiInput
import ru.hollowhorizon.hc.client.handlers.TickHandler
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.imgui.ImGuiHandler
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hc.client.utils.mcText
import ru.hollowhorizon.hollowengine.client.keys.Key
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager

abstract class ImGuiScreen(val saveFile: String = "") : Screen("".mcText), HasImGuiInput {
    val parent = Minecraft.getInstance().screen
    var isLoaded = false

    val file by lazy {
        DirectoryManager.guiCache.resolve("$saveFile.ini")
    }

    private var fadeTime = 0.0
    override fun init() {
        super.init()
        fadeTime = Blaze3D.getTime()
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics)
        val alpha = (Blaze3D.getTime() - fadeTime).toFloat().coerceAtMost(1f)
        ImGui.getStyle().alpha = Interpolation.EXPO_OUT(alpha)
        ImGui.pushStyleColor(ImGuiCol.ModalWindowDimBg, 0x88000000.toInt())
        ImGuiHandler.drawFrame {
            if (!isLoaded && saveFile.isNotEmpty()) {
                isLoaded = true
                if (file.exists()) ImGui.loadIniSettingsFromMemory(file.readText())
            }
            draw()
            if (saveFile.isNotEmpty() && TickHandler.currentTicks % 60 == 0) {
                file.writeText(ImGui.saveIniSettingsToMemory())
            }
        }
        ImGui.popStyleColor()

    }

    abstract fun Graphics.draw()

    override fun isPauseScreen() = false

    override fun onClose() {
        Minecraft.getInstance().setScreen(parent)
    }
}