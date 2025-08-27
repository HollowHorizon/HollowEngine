package ru.hollowhorizon.hc.common.events.level

import net.minecraft.world.level.Level
import ru.hollowhorizon.hc.common.events.Event

open class LevelEvent(val level: Level) : Event {
    class Save(level: Level) : LevelEvent(level)
    class Load(level: Level) : LevelEvent(level)
}