package ru.hollowhorizon.hollowengine.common.events.level

import net.minecraft.world.level.Level
import ru.hollowhorizon.hollowengine.common.events.Event
import ru.hollowhorizon.hollowengine.common.events.factory.EventHandler
import ru.hollowhorizon.hollowengine.common.scripting.katari.binding.ScriptBinding

@ScriptBinding
open class LevelEvent(val level: Level) : Event {
    @ScriptBinding
    class Save(level: Level) : LevelEvent(level) {
        companion object : EventHandler<Save>()
    }

    @ScriptBinding
    class Load(level: Level) : LevelEvent(level) {
        companion object : EventHandler<Load>()
    }
}
