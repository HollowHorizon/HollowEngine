package ru.hollowhorizon.hollowstory.cutscenes

import ru.hollowhorizon.hollowstory.story.StoryEvent
import ru.hollowhorizon.hollowstory.story.StoryTeam

class CutsceneManager {
    companion object {
        fun startCutscene(team: StoryTeam, cutscene: String, storyEvent: StoryEvent? = null): HollowCutscene {

            return HollowCutscene()
        }
    }
}