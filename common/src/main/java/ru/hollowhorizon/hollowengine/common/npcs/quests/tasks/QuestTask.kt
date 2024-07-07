package ru.hollowhorizon.hollowengine.common.npcs.quests.tasks

import imgui.ImGui
import imgui.flag.ImGuiStyleVar
import imgui.type.ImString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hollowengine.client.gui.npcs.quests.QuestRenderer

@Serializable
@Polymorphic(QuestTask::class)
abstract class AbstractQuestTask : QuestTask {
    override var name: String = javaClass.simpleName
    override var description = ""
    override var completeText = ""
    override var completeAnimation = ""

    @Transient
    val nameBuffer = ImString(100)
    @Transient
    val textBuffer = ImString(500)

    override fun drawEditor() {
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 2f)
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 5f)

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())

        ImGuiMethods.textShadow("Название задания: ")
        nameBuffer.set(name)
        ImGui.inputText("##name", nameBuffer)
        name = nameBuffer.get()

        ImGui.separator()

        ImGuiMethods.textShadow("Описание задания: ")
        textBuffer.set(description)
        ImGui.inputTextMultiline("##desc", textBuffer, ImGui.getContentRegionAvailX(), 100f)
        description = textBuffer.get()

        ImGui.separator()

        ImGuiMethods.textShadow("Сообщение при выполенении задания: ")
        textBuffer.set(completeText)
        ImGui.inputTextMultiline("##complete_desc", textBuffer, ImGui.getContentRegionAvailX(), 100f)
        completeText = textBuffer.get()

        ImGuiMethods.textShadow("Название задания: ")
        nameBuffer.set(completeAnimation)
        ImGui.inputText("##anim", nameBuffer)
        completeAnimation = nameBuffer.get()

        ImGui.separator()

        ImGui.popItemWidth()

        ImGui.popStyleVar(2)
    }
}

interface QuestTask {
    var name: String
    var description: String
    var completeText: String
    var completeAnimation: String

    val icon: ItemStack get() = ItemStack.EMPTY

    fun check(player: Player): Boolean

    fun complete(player: Player): Boolean

    fun drawEditor() {}
}