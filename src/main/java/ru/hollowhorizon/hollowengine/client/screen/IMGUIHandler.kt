package ru.hollowhorizon.hollowengine.client.screen

import imgui.*
import imgui.extension.imnodes.ImNodes
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiConfigFlags
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.gl3.ImGuiImplGl3
import imgui.glfw.ImGuiImplGlfw
import net.minecraft.client.Minecraft
import net.minecraft.util.ResourceLocation
import org.lwjgl.glfw.GLFW
import ru.hollowhorizon.hollowengine.client.screen.imgui.lockLists
import ru.hollowhorizon.hollowengine.client.screen.imgui.orEquals

object IMGUIHandler {
    private lateinit var font: ImFont
    private val imGuiGlfw = ImGuiImplGlfw()
    private val imGuiGl3 = ImGuiImplGl3()

    var initialized = false

    fun render(x: Int, y: Int, width: Int, height: Int, task: () -> Unit) {
        imGuiGlfw.newFrame()
        ImGui.newFrame()

        ImGui.pushFont(font)

        val flags = ImGuiWindowFlags.NoNavFocus.orEquals(
            ImGuiWindowFlags.NoTitleBar,
            ImGuiWindowFlags.NoCollapse,
            ImGuiWindowFlags.NoResize,
            ImGuiWindowFlags.NoMove,
            ImGuiWindowFlags.NoBringToFrontOnFocus
        )
        ImGui.setNextWindowPos(x.toFloat(), y.toFloat())
        ImGui.setNextWindowSize(
            width * Minecraft.getInstance().options.guiScale.toFloat(),
            height * Minecraft.getInstance().options.guiScale.toFloat()
        )
        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)
        ImGui.begin("Window", flags)

        ImGui.setNextWindowViewport(ImGui.getMainViewport().id)
        ImGui.popStyleVar()

        task()

        lockLists()

        ImGui.end()

        ImGui.popFont()

        ImGui.render()
        imGuiGl3.renderDrawData(ImGui.getDrawData())
        if (ImGui.getIO().hasConfigFlags(ImGuiConfigFlags.ViewportsEnable)) {
            val backupPtr = GLFW.glfwGetCurrentContext()
            ImGui.updatePlatformWindows()
            ImGui.renderPlatformWindowsDefault()
            GLFW.glfwMakeContextCurrent(backupPtr)
        }
    }

    fun load() {
        if (!initialized) {
            initImGui()
            imGuiGlfw.init(Minecraft.getInstance().window.window, true);
            if (!Minecraft.ON_OSX) {
                imGuiGl3.init("#version 410")
            } else {
                imGuiGl3.init("#version 120") //Using version of #120 for mac support (max allowed version otherwise exception on mac)
            }
            initialized = true
            println("Created the render context!")
            ImNodes.createContext();
        }
    }

    private fun initImGui() {
        ImGui.createContext()
        setupStyle(ImGui.getStyle())
        val io = ImGui.getIO()
        io.iniFilename = null
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard)
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable)
        io.configViewportsNoTaskBarIcon = true
    }

    private fun setupStyle(style: ImGuiStyle) {
        style.windowPadding.set(15f, 15f)
        style.framePadding.set(5.0f, 5.0f)
        style.itemSpacing.set(12.0f, 8.0f)
        style.itemInnerSpacing.set(8f, 6f)
        style.windowRounding = 0f
        style.indentSpacing = 25f
        style.scrollbarSize = 15.0f
        style.scrollbarRounding = 9.0f
        style.grabRounding = 3.0f
        setColor(ImGuiCol.Text, ImVec4(0.80f, 0.80f, 0.83f, 1.00f))
        setColor(ImGuiCol.TextDisabled, ImVec4(0.24f, 0.23f, 0.29f, 1.00f))
        setColor(ImGuiCol.WindowBg, ImVec4(0.06f, 0.05f, 0.07f, 0.50f))
        setColor(ImGuiCol.ChildBg, ImVec4(0.07f, 0.07f, 0.09f, 1.00f))
        setColor(ImGuiCol.PopupBg, ImVec4(0.07f, 0.07f, 0.09f, 1.00f))
        setColor(ImGuiCol.Border, ImVec4(0.80f, 0.80f, 0.83f, 0.88f))
        setColor(ImGuiCol.BorderShadow, ImVec4(0.92f, 0.91f, 0.88f, 0.00f))
        setColor(ImGuiCol.FrameBg, ImVec4(0.10f, 0.09f, 0.12f, 1.00f))
        setColor(ImGuiCol.FrameBgHovered, ImVec4(0.24f, 0.23f, 0.29f, 1.00f))
        setColor(ImGuiCol.FrameBgActive, ImVec4(0.56f, 0.56f, 0.58f, 1.00f))
        setColor(ImGuiCol.TitleBg, ImVec4(0.10f, 0.09f, 0.12f, 1.00f))
        setColor(ImGuiCol.TitleBgCollapsed, ImVec4(1.00f, 0.98f, 0.95f, 0.75f))
        setColor(ImGuiCol.TitleBgActive, ImVec4(0.07f, 0.07f, 0.09f, 1.00f))
        setColor(ImGuiCol.MenuBarBg, ImVec4(0.10f, 0.09f, 0.12f, 1.00f))
        setColor(ImGuiCol.ScrollbarBg, ImVec4(0.10f, 0.09f, 0.12f, 1.00f))
        setColor(ImGuiCol.ScrollbarGrab, ImVec4(0.80f, 0.80f, 0.83f, 0.31f))
        setColor(ImGuiCol.ScrollbarGrabHovered, ImVec4(0.56f, 0.56f, 0.58f, 1.00f))
        setColor(ImGuiCol.ScrollbarGrabActive, ImVec4(0.06f, 0.05f, 0.07f, 1.00f))
        setColor(ImGuiCol.CheckMark, ImVec4(0.80f, 0.80f, 0.83f, 0.31f))
        setColor(ImGuiCol.SliderGrab, ImVec4(0.80f, 0.80f, 0.83f, 0.31f))
        setColor(ImGuiCol.SliderGrabActive, ImVec4(0.06f, 0.05f, 0.07f, 1.00f))
        setColor(ImGuiCol.Button, ImVec4(0.10f, 0.09f, 0.12f, 1.00f))
        setColor(ImGuiCol.ButtonHovered, ImVec4(0.24f, 0.23f, 0.29f, 1.00f))
        setColor(ImGuiCol.ButtonActive, ImVec4(0.56f, 0.56f, 0.58f, 1.00f))
        setColor(ImGuiCol.Header, ImVec4(0.10f, 0.09f, 0.12f, 1.00f))
        setColor(ImGuiCol.HeaderHovered, ImVec4(0.56f, 0.56f, 0.58f, 1.00f))
        setColor(ImGuiCol.HeaderActive, ImVec4(0.06f, 0.05f, 0.07f, 1.00f))
        setColor(ImGuiCol.ResizeGrip, ImVec4(0.00f, 0.00f, 0.00f, 0.00f))
        setColor(ImGuiCol.ResizeGripHovered, ImVec4(0.56f, 0.56f, 0.58f, 1.00f))
        setColor(ImGuiCol.ResizeGripActive, ImVec4(0.06f, 0.05f, 0.07f, 1.00f))
        setColor(ImGuiCol.PlotLines, ImVec4(0.40f, 0.39f, 0.38f, 0.63f))
        setColor(ImGuiCol.PlotLinesHovered, ImVec4(0.25f, 1.00f, 0.00f, 1.00f))
        setColor(ImGuiCol.PlotHistogram, ImVec4(0.40f, 0.39f, 0.38f, 0.63f))
        setColor(ImGuiCol.PlotHistogramHovered, ImVec4(0.25f, 1.00f, 0.00f, 1.00f))
        setColor(ImGuiCol.TextSelectedBg, ImVec4(0.25f, 1.00f, 0.00f, 0.43f))
        setColor(ImGuiCol.ModalWindowDimBg, ImVec4(1.00f, 0.98f, 0.95f, 0.73f))

        font = loadFont(ResourceLocation("hollowengine:fonts/roboto.ttf"), 16f)
    }

    /**
     * [ImGuiCol.Text] = Цвет для текста, который будет использоваться для всего меню.
     *
     * [ImGuiCol.TextDisabled] = Цвет для "не активного/отключенного текста".
     *
     * [ImGuiCol.WindowBg] = Цвет заднего фона.
     *
     * [ImGuiCol.PopupBg] = Цвет, который используется для заднего фона в ImGui::Combo и ImGui::MenuBar.
     *
     * [ImGuiCol.Border] = Цвет, который используется для обводки вашего меню.
     *
     * [ImGuiCol.BorderShadow] = Цвет для тени обводки.
     *
     * [ImGuiCol.FrameBg] = Цвет для ImGui::InputText и для заднего фона ImGui::Checkbox
     *
     * [ImGuiCol.FrameBgHovered] = Цвет,который используется практически так же что и тот, который выше, кроме того, что он изменяет цвет при наводке на ImGui::Checkbox.
     *
     * [ImGuiCol.FrameBgActive] = Активный цвет.
     *
     * [ImGuiCol.TitleBg] = Цвет для изменения главного места в самом верху меню.
     *
     * [ImGuiCol.TitleBgCollapsed] = Свернутый цвет тайтла.
     *
     * [ImGuiCol.TitleBgActive] = Цвет активного окна тайтла, т.е если вы имеете меню с несколькими окнами, то этот цвет будет использоваться для окна, в котором вы будете находиться на данный момент.
     *
     * [ImGuiCol.MenuBarBg] = Цвет для меню бара. (Не во всех сурсах видел такое, но все же)
     *
     * [ImGuiCol.ScrollbarBg] = Цвет для заднего фона "полоски", через которую можно "листать" функции в софте по вертикале.
     *
     * [ImGuiCol.ScrollbarGrab] = Цвет для сколл бара, т.е для "полоски", которая используется для передвижения меню по вертикали.
     *
     * [ImGuiCol.ScrollbarGrabHovered] = Цвет для "свернутого/не используемого" скролл бара.
     *
     * [ImGuiCol.ScrollbarGrabActive] = Цвет для "активной" деятельности в том окне, где находится скролл бар.
     *
     * [ImGuiCol.ComboBg] = Цвет для заднего фона для ImGui::Combo.
     *
     * [ImGuiCol.CheckMark] = Цвет для вашего ImGui::Checkbox.
     *
     * [ImGuiCol.SliderGrab] = Цвет для ползунка ImGui::SliderInt и ImGui::SliderFloat.
     *
     * [ImGuiCol.SliderGrabActive] = Цвет ползунка, который будет отображаться при использовании SliderFloat и SliderInt.
     *
     * [ImGuiCol.Button] = цвет для кнопки.
     *
     * [ImGuiCol.ButtonHovered] = Цвет, при наведении на кнопку.
     *
     * [ImGuiCol.ButtonActive] = Используемый цвет кнопки.
     *
     * [ImGuiCol.Header] = Цвет для ImGui::CollapsingHeader.
     *
     * [ImGuiCol.HeaderHovered] = Цвет,при наведении на ImGui::CollapsingHeader.
     *
     * [ImGuiCol.HeaderActive] = Используемый цвет ImGui::CollapsingHeader.
     *
     * [ImGuiCol.Column] = Цвет для "полоски отделения" ImGui::Column и ImGui::NextColumn.
     *
     * [ImGuiCol.ColumnHovered] = Цвет,при наведении на "полоску отделения" ImGui::Column и ImGui::NextColumn.
     *
     * [ImGuiCol.ColumnActive] = Используемый цвет для "полоски отделения" ImGui::Column и ImGui::NextColumn.
     *
     * [ImGuiCol.ResizeGrip] = Цвет для "треугольника" в правом нижнем углу, который используется для увеличения или уменьшения размеров меню.
     *
     * [ImGuiCol.ResizeGripHovered] = Цвет, при наведении на "треугольника" в правом нижнем углу, который используется для увеличения или уменьшения размеров меню.
     *
     * [ImGuiCol.ResizeGripActive] = Используемый цвет для "треугольника" в правом нижнем углу, который используется для увеличения или уменьшения размеров меню.
     *
     * [ImGuiCol.CloseButton] = Цвет для кнопки-закрытия меню.
     *
     * [ImGuiCol.CloseButtonHovered] = Цвет, при наведении на кнопку-закрытия меню.
     *
     * [ImGuiCol.CloseButtonActive] = Используемый цвет для кнопки-закрытия меню.
     *
     * [ImGuiCol.TextSelectedBg] = Цвет выбранного текста,в ImGui::MenuBar.
     *
     * [ImGuiCol.ModalWindowDarkening] = Цвет "Затемнения окна" вашего меню.
     *
     * Редко вижу данные обозначения, но все таки решил их сюда поместить.
     * [ImGuiCol.Tab] = Цвет для табов в меню.
     *
     * [ImGuiCol.TabActive] = Активный цвет табов, т.е при нажатии на таб у вас будет этот цвет.
     *
     * [ImGuiCol.TabHovered] = Цвет, который будет отображаться при наведении на таб.
     *
     * [ImGuiCol.TabSelected] = Цвет,при котором, используется тогда, когда вы будете находиться в одном из табов.
     *
     * [ImGuiCol.TabText] = Цвет текста, который распространяется только на табы.
     *
     * [ImGuiCol.TabTextActive] = Активный цвет текста для табов.
     */

    fun loadFont(font: ResourceLocation, size: Float): ImFont {
        val fontStream = Minecraft.getInstance().resourceManager.getResource(font).inputStream
        val fontBytes = fontStream.readBytes()
        fontStream.close()
        return ImGui.getIO().fonts.addFontFromMemoryTTF(
            fontBytes,
            size,
            ImFontConfig(),
            ImGui.getIO().fonts.glyphRangesCyrillic
        )
    }

    private fun setColor(colorIndex: Int, color: ImVec4) {
        val style = ImGui.getStyle()
        style.setColor(colorIndex, color.x, color.y, color.z, color.w)
    }
}

fun Int.toImVec4(): ImVec4 {
    //from hex
    val r = (this shr 16 and 0xFF) / 255.0f
    val g = (this shr 8 and 0xFF) / 255.0f
    val b = (this and 0xFF) / 255.0f
    val a = (this shr 24 and 0xFF) / 255.0f
    return ImVec4(r, g, b, a)
}