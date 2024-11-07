package ru.hollowhorizon.hollowengine.client.gui

import imgui.extension.nodeditor.NodeEditorConfig
import imgui.extension.nodeditor.NodeEditorContext
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui
import net.minecraft.client.Minecraft
import net.minecraft.locale.Language
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hc.client.utils.open
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture
import ru.hollowhorizon.hc.common.events.Event
import ru.hollowhorizon.hc.common.events.SubscribeEvent
import ru.hollowhorizon.hc.common.events.post
import ru.hollowhorizon.hollowengine.client.gui.npcs.quests.QuestsGraphGui
import ru.hollowhorizon.hollowengine.client.gui.npcs.trading.TradeMenuGui
import ru.hollowhorizon.hollowengine.client.gui.scripting.sendToast
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

class NPCToolGui(val npc: NPCEntity) : ImGuiScreen() {
    val npcOptions = ArrayList<NpcOption>()

    override fun init() {
        super.init()

        npcOptions.clear()

        NpcOptionsEvent(npcOptions::add, npc).post()
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
            "Меню персонажа",
            ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize or ImGuiWindowFlags.NoCollapse or ImGuiWindowFlags.AlwaysAutoResize
        ) {
            val size =
                (ImGui.getContentRegionMax().x - ImGui.getStyle().itemSpacingX * 4) / 4 - ImGui.getStyle().framePaddingX * 2

            npcOptions.forEachIndexed { index, npcOption ->
                if (imageButton(npcOption.name, size)) npcOption.onClick()
                if ((index + 1) % 4 != 0) ImGui.sameLine()
            }
        }
        ImGui.popStyleVar(3)

    }

    fun imageButton(image: String, size: Float): Boolean {
        val isClicked = ImGui.imageButton("hollowengine:textures/gui/icons/$image.png".rl.toTexture().id.toLong(), size, size)
        ImGui.pushStyleVar(ImGuiStyleVar.PopupBorderSize, 3f)
        if (ImGui.isItemHovered()) ImGui.setTooltip(
            //? if >=1.20.1 {
            Language.getInstance().getOrDefault("npc_tool.$image", "No description available.")
            //?} else {
            /*Language.getInstance().getOrDefault("npc_tool.$image")
            *///?}
        )
        ImGui.popStyleVar()
        return isClicked
    }
}

val config = NodeEditorConfig().apply {
    settingsFile = "hollowengine/nodes.json"
}
val context = NodeEditorContext(config.ptr)

@SubscribeEvent(100)
fun registerNpcOptions(event: NpcOptionsEvent) {
    event.register(NpcOption("options") { NPCCreatorGui(event.npc, event.npc.id).open() })
    event.register(NpcOption("behavior") {
        Minecraft.getInstance().player?.sendToast("Временно отключено, используйте скрипты.".literal)
        //NpcBehaviorGui().open()
    })

    event.register(NpcOption("pose_editor") {
        Minecraft.getInstance().player?.sendToast("Отключено, будет реализовано в редакторе катсцен.".literal)
    })
    event.register(NpcOption("trades") {
        TradeMenuGui(event.npc, true).open()
    })
    event.register(NpcOption("quests") {
        QuestsGraphGui(event.npc).open()
    })
}

class NpcOption(val name: String, val onClick: () -> Unit)

class NpcOptionsEvent(private val generator: (NpcOption) -> Unit, val npc: NPCEntity) : Event {
    fun register(npc: NpcOption) {
        generator(npc)
    }
}