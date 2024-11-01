package ru.hollowhorizon.hollowengine.client.gui.npcs.dialogue

import imgui.ImGui.*
import imgui.ImVec2
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui
import net.minecraft.client.Minecraft
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.models.internal.Transform
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.utils.get
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.client.gui.ImGuiScreen
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.model
import ru.hollowhorizon.hollowengine.common.scripting.story.functions.npcs.transform
import kotlin.math.min

object DialogueGui : ImGuiScreen() {
    private var scale = 1f
    val vitalik = NPCEntity(Minecraft.getInstance().level!!).apply {
        model = "hollowengine:models/monster.gltf"
        this[AnimatedEntityCapability::class.java].transform = Transform(sX = 0.75f, sY = 0.75f, sZ = 0.75f)
    }

    override fun init() {
        super.init()

        val window = Minecraft.getInstance().window
        scale = min(window.width / 480f, window.height * 0.9f)
    }

    override fun Graphics.draw() {
        vitalik.tickCount = Minecraft.getInstance().player!!.tickCount
        val window = Minecraft.getInstance().window
        setNextWindowPos(0f, 0f)
        setNextWindowSize(window.width.toFloat(), window.height.toFloat())

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)

        val fontSize = 35

        withFontSize(fontSize) {

            centredWindow(args = ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoTitleBar or ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoScrollbar) {
                val offsetX = (ImGui.getMousePosX() / ImGui.getWindowSizeX() - 0.5f) * 2 * -1
                val offsetY = (getMousePosY() / getWindowSizeY() - 0.5f) * 2 * -1
                ImGui.setCursorPos(getCursorPosX() + offsetX * window.width * 0.01f, getCursorPosY() + offsetY * window.height * 0.01f)
                image(
                    "hollowengine:textures/gui/npc_menu/background.png".rl,
                    window.width.toFloat(),
                    window.height.toFloat()
                )

                val size = ImVec2(window.width.toFloat()  * 0.9f, window.height.toFloat() * 0.9f)
                setCursorPos(window.width / 2f - size.x / 2f, window.height / 2f - size.y / 2f)
                ImGui.setCursorPos(getCursorPosX() + offsetX * window.width * 0.015f, getCursorPosY() + offsetY * window.height * 0.015f)
                entity(vitalik, size.x, size.y)

                val width = 0.8854167f
                ImGui.setCursorPos(window.width / 2f - (window.width * width) / 2f, window.height * 0.9f - window.width * width * 0.14588235f)
                ImGui.setCursorPos(getCursorPosX() + offsetX * window.width * 0.02f, getCursorPosY() + offsetY * window.height * 0.02f)
                pushCursor()
                image(
                    "hollowengine:textures/gui/dialogues/dialogue_box.png".rl,
                    window.width * width,
                    window.width * width * 0.14588235f
                )
                popCursor()
                setCursorPos(getCursorPosX() + 65f, getCursorPosY() + 60f)
                textShadow("Бу! Испугался? Не бойся, я друг, я тебя не обижу. Иди сюда, иди ко мне,\nсядь рядом со мной, посмотри мне в глаза. Ты видишь меня? Я тоже тебя\nвижу. Давай смотреть друг на друга до тех пор, пока наши глаза не\nустанут. Ты не хочешь? Почему? Что-то не так?")

                val nameOffset = 0.016470589f
                val nameWidth = window.width * 0.1875f
                val nameRatio = 0.22222222f
                ImGui.setCursorPos(window.width / 2f - (window.width * width) / 2f + (window.width * nameOffset), window.height * 0.9f - window.width * width * 0.14588235f - nameWidth * nameRatio)
                ImGui.setCursorPos(getCursorPosX() + offsetX * window.width * 0.02f, getCursorPosY() + offsetY * window.height * 0.02f)
                pushCursor()
                image(
                    "hollowengine:textures/gui/dialogues/character_name.png".rl,
                    nameWidth,
                    nameWidth * nameRatio
                )
                popCursor()
                setCursorPos(getCursorPosX() + 65f, getCursorPosY() + 22f)
                textShadow("Виталик?..")
            }
        }

        popStyleVar()
    }
}