package ru.hollowhorizon.hollowengine.common.story.episode

import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import kotlinx.serialization.Serializable
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.client.utils.nbt.ForUuid
import ru.hollowhorizon.hc.common.coroutines.scopeAsync
import ru.hollowhorizon.hollowengine.common.entities.NPCEntity
import ru.hollowhorizon.hollowengine.common.story.episode.actions.Action
import java.util.*

@Serializable
class Episode {
    val npcs = ArrayList<@Serializable(ForUuid::class) UUID>()
    private val actions = ArrayList<Action>()
    var index = 0

    fun edit() {
        ImGui.columns(2, "Эпизод", false)

        ImGui.setColumnWidth(0, 330f)

        if (ImGui.treeNodeEx("Сцена", ImGuiTreeNodeFlags.SpanFullWidth)) {
            ImGui.selectable("Установить время")
            ImGui.selectable("Изменить погоду")
            ImGui.selectable("Запустить команду")
            ImGui.treePop()
        }

        if (ImGui.treeNodeEx("Персонажи", ImGuiTreeNodeFlags.SpanFullWidth)) {
            ImGui.selectable("Написать в чат")
            ImGui.selectable("Запустить анимацию")
            ImGui.selectable("Остановить анимацию")
            ImGui.selectable("Идти к цели")
            ImGui.selectable("Посмотреть на цель")
            ImGui.treePop()
        }

        if (ImGui.treeNodeEx("Игрок", ImGuiTreeNodeFlags.SpanFullWidth)) {
            ImGui.selectable("Сообщение в чат")
            ImGui.selectable("Выбор в чате")
            ImGui.selectable("Выбор в интерфейсе")
            ImGui.treePop()
        }

        if (ImGui.treeNodeEx("Эффекты", ImGuiTreeNodeFlags.SpanFullWidth)) {
            ImGui.selectable("Проиграть звук")
            ImGui.selectable("Проиграть видео")
            ImGui.selectable("Применить эффект")
            ImGui.selectable("Применить шейдер")
            ImGui.treePop()
        }


        ImGui.nextColumn()

        actions.forEach { it.edit(this) }
    }

    fun runAction(server: MinecraftServer) = scopeAsync {
        actions.subList(index, actions.size).forEach {
            it.run(this@Episode, server)
            index++
        }
    }

}

fun Level.getNpc(id: UUID): NPCEntity =
    (if (this.isClientSide) (this as ClientLevel).entitiesForRendering().find { it.uuid == id }
    else (this as ServerLevel).getEntity(id)) as? NPCEntity
        ?: throw IllegalArgumentException("NPC $id is not found!")