package ru.hollowhorizon.hollowengine.common.story.episode.actions

import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImInt
import imgui.type.ImString
import kotlinx.serialization.Serializable
import net.minecraft.client.Minecraft
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.ai.targeting.TargetingConditions
import ru.hollowhorizon.hc.api.utils.Polymorphic
import ru.hollowhorizon.hc.client.utils.literal
import ru.hollowhorizon.hollowengine.common.story.episode.Episode
import ru.hollowhorizon.hollowengine.common.story.episode.getNpc

private val npcIdBuffer = ImInt()
private val textBuffer = ImString(256)

@Serializable
@Polymorphic(Action::class)
class SayAction : Action {
    var npcId = 0
    var message = ""

    override fun edit(ep: Episode) {
        if(ImGui.treeNodeEx("Написать в чат##${hashCode()}", ImGuiTreeNodeFlags.SpanFullWidth)) {
            ImGui.beginChild("##say_action", 1000f, 100f, true)
            npcIdBuffer.set(npcId)
            ImGui.combo("Персонаж",
                npcIdBuffer,
                Minecraft.getInstance().level?.entitiesForRendering()?.filter { it.uuid in ep.npcs }
                    ?.map { it.name.string }
                    ?.toTypedArray() ?: emptyArray())
            npcId = npcIdBuffer.get()

            textBuffer.set(message)
            ImGui.inputText("Сообщение", textBuffer)
            message = textBuffer.get()
            ImGui.endChild()

            ImGui.treePop()
        }
    }

    override suspend fun run(ep: Episode, server: MinecraftServer) {
        val uuid = ep.npcs[npcId]
        val npc = server.overworld().getNpc(uuid)

        npc.level()
            .getNearbyPlayers(TargetingConditions.forNonCombat(), npc, npc.boundingBox.expandTowards(50.0, 50.0, 50.0))
            .forEach { it.sendSystemMessage("[${npc.name.string}] $message".literal) }
    }
}