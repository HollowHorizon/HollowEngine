package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.scripting.story.nodes.base.next
import ru.hollowhorizon.hollowengine.cutscenes.replay.Replay
import ru.hollowhorizon.hollowengine.cutscenes.replay.ReplayPlayer

infix fun NPCProperty.replay(file: () -> String) {
    builder.apply {
        next {
            val replay = Replay.fromFile(DirectoryManager.HOLLOW_ENGINE.resolve("replays").resolve(file()))
            ReplayPlayer(this@replay()).apply {
                saveEntity = true
                isLooped = false
                play(this@replay().level, replay)
            }
        }
    }
}