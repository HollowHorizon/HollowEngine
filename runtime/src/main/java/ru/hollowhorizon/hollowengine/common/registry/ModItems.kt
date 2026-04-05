package ru.hollowhorizon.hollowengine.common.registry

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.items.NpcTool
import ru.hollowhorizon.hollowengine.common.items.StoryTellerDimItem

object ModItems : HollowRegistry(HollowEngine.MODID) {
    val STORYTELLER_DIM_TELEPORTER by register("storyteller_dim_teleporter") { StoryTellerDimItem() }
    val NPC_TOOL by register("npc_tool") { NpcTool() }
}