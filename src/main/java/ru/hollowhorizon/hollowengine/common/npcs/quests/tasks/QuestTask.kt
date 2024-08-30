package ru.hollowhorizon.hollowengine.common.npcs.quests.tasks

import imgui.ImGui
import imgui.flag.ImGuiStyleVar
import imgui.type.ImString
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.imgui.Graphics
import ru.hollowhorizon.hc.client.utils.nbt.ForItemStack

@Serializable
@Polymorphic(QuestTask::class)
abstract class AbstractQuestTask : QuestTask {
    override var name: String = javaClass.simpleName
    override var description = ""
    override var completeText = ""
    override var completeAnimation = ""
    override var icon: @Serializable(ForItemStack::class) ItemStack = ItemStack.EMPTY

    @Transient
    val nameBuffer = ImString(100)

    @Transient
    val textBuffer = ImString(500)

    override fun drawEditor() {
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 2f)
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 5f)

        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())

        Graphics.textShadow("Название задания: ")
        nameBuffer.set(name)
        ImGui.inputText("##name", nameBuffer)
        name = nameBuffer.get()

        ImGui.separator()

        Graphics.textShadow("Описание задания: ")
        textBuffer.set(description)
        ImGui.inputTextMultiline("##desc", textBuffer, ImGui.getContentRegionAvailX(), 100f)
        description = textBuffer.get()

        ImGui.separator()

        Graphics.textShadow("Сообщение при выполенении задания: ")
        textBuffer.set(completeText)
        ImGui.inputTextMultiline("##complete_desc", textBuffer, ImGui.getContentRegionAvailX(), 100f)
        completeText = textBuffer.get()

        Graphics.textShadow("Название задания: ")
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

    var icon: ItemStack

    fun check(player: Player): Boolean

    fun complete(player: Player): Boolean

    fun drawEditor() {}
}