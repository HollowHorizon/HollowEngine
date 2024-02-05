package ru.hollowhorizon.hollowengine.common.compat.ftbquests

import dev.ftb.mods.ftblibrary.config.ConfigGroup
import dev.ftb.mods.ftbquests.events.QuestProgressEventData
import dev.ftb.mods.ftbquests.quest.Quest
import dev.ftb.mods.ftbquests.quest.TeamData
import dev.ftb.mods.ftbquests.quest.task.Task
import dev.ftb.mods.ftbquests.quest.task.TaskType
import dev.ftb.mods.ftbteams.FTBTeamsAPI
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.server.ServerLifecycleHooks
import ru.hollowhorizon.hc.client.utils.mcTranslate
import ru.hollowhorizon.hollowengine.common.events.StoryHandler
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.fromReadablePath
import ru.hollowhorizon.hollowengine.common.scripting.story.runScript
import java.time.LocalDateTime
import java.util.Date


class StoryEventTask(quest: Quest) : Task(quest) {
    var storyEventName = ""
    var lastCheckExists = false
    override fun getType(): TaskType = FTBQuestsSupport.STORY_EVENT

    override fun writeData(nbt: CompoundTag) {
        super.writeData(nbt)
        nbt.putString("story_event", storyEventName)
    }

    override fun readData(nbt: CompoundTag) {
        super.readData(nbt)
        storyEventName = nbt.getString("story_event")
    }


    override fun writeNetData(buffer: FriendlyByteBuf) {
        super.writeNetData(buffer)
        buffer.writeUtf(storyEventName)
    }

    override fun readNetData(buffer: FriendlyByteBuf) {
        super.readNetData(buffer)
        storyEventName = buffer.readUtf()
    }

    @OnlyIn(Dist.CLIENT)
    override fun getConfig(config: ConfigGroup) {
        super.getConfig(config)
        config.addString("story_event", storyEventName, { input: String -> storyEventName = input }, "")
    }

    override fun getAltTitle() = storyEventName.mcTranslate

    override fun getMaxProgress(): Long {
        return 1L
    }

    override fun autoSubmitOnPlayerTick(): Int {
        return 20
    }

    override fun submitTask(teamData: TeamData, player: ServerPlayer, craftedItem: ItemStack) {
        if (teamData.isCompleted(this)) return
        val team = FTBTeamsAPI.getManager().getTeamByID(teamData.uuid) ?: return

        val hasEvent = StoryHandler.getActiveEvents(team).contains(storyEventName)

        if(!lastCheckExists && !hasEvent) {
            runScript(ServerLifecycleHooks.getCurrentServer(), team, storyEventName.fromReadablePath())
        }

        teamData.setProgress(this, if(!hasEvent && lastCheckExists) 1L else 0L)

        lastCheckExists = hasEvent
    }

    override fun onStarted(data: QuestProgressEventData<*>) {
        super.onStarted(data)
        val team = FTBTeamsAPI.getManager().getTeamByID(data.teamData.uuid) ?: return


    }

    override fun formatProgress(teamData: TeamData, progress: Long): String {
        val team = FTBTeamsAPI.getManager().getTeamByID(teamData.uuid) ?: return "???"
        val event = StoryHandler.getEventByName(team, storyEventName) ?: return "???"
        val realProgress = (event.currentIndex / event.nodes.size.toFloat()) * 100f
        return realProgress.toInt().toString() + "%"
    }
}