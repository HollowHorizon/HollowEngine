package ru.hollowhorizon.hollowengine.common.compat.ftbquests

import dev.ftb.mods.ftblibrary.icon.Icons
import dev.ftb.mods.ftbquests.quest.task.TaskTypes
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hollowengine.HollowEngine.Companion.MODID

object FTBQuestsSupport {
    val STORY_EVENT = TaskTypes.register("$MODID:story_event".rl, ::StoryEventTask) { Icons.NOTES }
}