package ru.hollowhorizon.hollowengine.common.scripting.story.nodes.npcs

import ru.hollowhorizon.hollowengine.common.entities.NPCEntity

class NPCProperty(val npc: () -> NPCEntity) : () -> NPCEntity by npc
