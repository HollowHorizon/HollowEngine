package ru.hollowhorizon.hollowengine.client.gui.npcs

import imgui.ImGui.*
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.internal.ImGui
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.models.internal.animations.PlayMode
import ru.hollowhorizon.hc.client.models.internal.manager.AnimatedEntityCapability
import ru.hollowhorizon.hc.client.models.internal.manager.AnimationLayer
import ru.hollowhorizon.hc.client.models.internal.manager.LayerMode
import ru.hollowhorizon.hc.client.utils.*
import ru.hollowhorizon.hc.client.utils.math.Interpolation
import ru.hollowhorizon.hollowengine.client.gui.ImGuiScreen
import ru.hollowhorizon.hollowengine.client.gui.npcs.quests.QuestsGraphGui
import ru.hollowhorizon.hollowengine.client.gui.npcs.trading.TradeMenuGui
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import kotlin.math.min

class NPCMenuGui(val npc: NPCEntity) : ImGuiScreen() {
    private var scale = 1f
    private val sizes = HashMap<Int, ButtonData>()

    override fun init() {
        super.init()

        val window = Minecraft.getInstance().window
        scale = min(window.width / 480f, window.height * 0.9f)
    }

    override fun Graphics.draw() {
        val window = Minecraft.getInstance().window
        setNextWindowPos(0f, 0f)
        setNextWindowSize(window.width.toFloat(), window.height.toFloat())

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f)

        val fontSize = when {
            scale < 1.5f -> 20
            scale < 2f -> 30
            scale < 2.5f -> 30
            scale > 3f -> 50
            else -> 40
        }

        withFontSize(fontSize) {

            centredWindow {
                image(
                    "hollowengine:textures/gui/npc_menu/background.png".rl,
                    window.width.toFloat(),
                    window.height.toFloat()
                )

                drawContextMenu()
                drawNpcPreview()
            }
        }

        popStyleVar()
    }

    fun drawContextMenu() {
        val buttons = arrayOf(
            "talk",
            "trade",
            "quests",
            "invite"
        )

        val size = sizes.size * 27 * scale - 3 * scale

        pushStyleVar(ImGuiStyleVar.ItemSpacing, 3 * scale, 3 * scale)
        setCursorPos(70 * scale, getWindowSizeY() / 2 - size / 2)

        buttons.forEachIndexed { index, s ->
            if (drawButton(index, s)) {
                when (index) {
                    0 -> {
                        onClose()
                        Minecraft.getInstance().player?.sendSystemMessage("[Ирис] Мне не о чем с тобой поговорить.".mcText)
                        npc[AnimatedEntityCapability::class].layers.add(
                            AnimationLayer(
                                "yes",
                                LayerMode.ADD,
                                PlayMode.ONCE,
                                1f
                            )
                        )
                    }

                    1 -> {
                        TradeMenuGui(npc, false).open()
                    }

                    2 -> {
                        QuestsGraphGui(npc, false).open()
                    }

                    3 -> {
                        onClose()
                        Minecraft.getInstance().player?.sendSystemMessage("[Ирис] Мне Халва говорил не вступать в группу с какими-то незнакомыми дяденьками...".mcText)
                        npc[AnimatedEntityCapability::class].layers.add(
                            AnimationLayer(
                                "no",
                                LayerMode.ADD,
                                PlayMode.ONCE,
                                1f
                            )
                        )
                    }
                }
            }
            setCursorPosX(70 * scale)
        }

        popStyleVar()
    }

    fun drawButton(i: Int, desc: String): Boolean {
        val name = Language.getInstance().getOrDefault("npc_menu.$desc")
        val pos = getCursorScreenPos()
        val anim = sizes.computeIfAbsent(i) { ButtonData(0f) }

        val hovered = isMouseHoveringRect(
            pos.x,
            pos.y,
            pos.x + 205 * scale,
            pos.y + 24 * scale
        )

        if (hovered) anim.size += 0.05f
        else anim.size -= 0.05f

        anim.size = anim.size.coerceIn(0f, 2f)

        val size = Interpolation.QUAD_OUT(anim.size / 2f)

        val textSize = calcTextSize(name)
        textSize.plus(textSize.x * 0.1f * size, textSize.y * 0.1f * size)

        pos.minus(20.5f * size, 2.4f * size)

        pos.minus(38 * scale - 8 * scale * size, 0f)

        ImGui.getWindowDrawList()
            .addImage(
                "hollowengine:textures/gui/npc_menu/$desc.png".rl.toTexture().id,
                pos.x,
                pos.y,
                pos.x + 32 * scale + 6.4f * size,
                pos.y + 24 * scale + 4.8f * size,
                0f, 0f, 1f, 1f,
                colorConvertFloat4ToU32(1f, 1f, 1f, ImGui.getStyle().alpha)
            )

        pos.plus(38 * scale - 8 * scale * size, 0f)

        pos.plus(215 * scale - 4 * scale * size, 0f)

        ImGui.getWindowDrawList()
            .addImage(
                "hollowengine:textures/gui/npc_menu/cursor.png".rl.toTexture().id,
                pos.x,
                pos.y,
                pos.x + 22 * scale + 4.4f * size,
                pos.y + 24 * scale + 4.8f * size,
                0f, 0f, 1f, 1f,
                colorConvertFloat4ToU32(1f, 1f, 1f, size)
            )
        pos.minus(215 * scale - 4 * scale * size, 0f)

        ImGui.getWindowDrawList()
            .addImage(
                "hollowengine:textures/gui/npc_menu/button.png".rl.toTexture().id,
                pos.x,
                pos.y,
                pos.x + 205 * scale + 41f * size,
                pos.y + 24 * scale + 4.8f * size,
                0f, 0f, 1f, 1f,
                colorConvertFloat4ToU32(1f, 1f, 1f, ImGui.getStyle().alpha)
            )

        pos.plus(20.5f * size, 2.4f * size)

        setCursorScreenPos(pos.x + 102.5f * scale - textSize.x / 2, pos.y + 12f * scale - textSize.y / 2)

        val list = getWindowDrawList()
        val color = getStyle().getColor(ImGuiCol.Text)
        val fontSize = getFontSize()
        list.addText(
            getFont(),
            (fontSize + fontSize * 0.1f * size).toInt(),
            pos.x + 102.5f * scale - textSize.x / 2 + 2.5f,
            pos.y + 12f * scale - textSize.y / 2 + 2.5f,
            colorConvertFloat4ToU32(
                color.x * 0.5f,
                color.y * 0.5f,
                color.z * 0.5f,
                color.w * 0.5f * ImGui.getStyle().alpha
            ),
            name
        )
        list.addText(
            getFont(),
            (fontSize + fontSize * 0.1f * size).toInt(),
            pos.x + 102.5f * scale - textSize.x / 2,
            pos.y + 12f * scale - textSize.y / 2,
            colorConvertFloat4ToU32(color.x, color.y, color.z, color.w * ImGui.getStyle().alpha),
            name
        )

        setCursorScreenPos(pos.x, pos.y)
        return invisibleButton("#$name", 205 * scale, 24 * scale)
    }

    fun drawNpcPreview() {
        setCursorPos(334 * scale, 40 * scale)

        image("hollowengine:textures/gui/npc_menu/nickname.png".rl.toTexture().id, 90f * scale, 20f * scale)

        val size = calcTextSize(npc.name)
        setCursorPos(379 * scale - size.x / 2, 50 * scale - size.y / 2)
        Graphics.textShadow(npc.name)

        setCursorPos(320f * scale, 63 * scale)

        image("hollowengine:textures/gui/npc_menu/character.png".rl.toTexture().id, 118f * scale, 155f * scale)
        setCursorPos(331f * scale, 76 * scale)
        Graphics.entity(
            npc,
            96 * scale,
            136 * scale,
            scale = 1.25f,
            offsetY = 50 * scale,
            alpha = ImGui.getStyle().alpha
        )
    }

    class ButtonData(var size: Float)
}