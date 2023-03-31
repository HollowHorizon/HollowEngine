package ru.hollowhorizon.hollowengine.common.npcs

import ru.hollowhorizon.hollowengine.common.exceptions.StoryEventException

class NPCManager {
    companion object {
        fun fromName(name: String): NPCSettings {


            NPCStorage.NPC_STORAGE[name]?.let { return it }
            throw StoryEventException("NPC with name $name not found")
        }
    }
}