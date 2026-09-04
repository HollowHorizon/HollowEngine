package ru.hollowhorizon.hollowengine.common.registry

import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.api.AutoModelType
import ru.hollowhorizon.hollowengine.common.items.NpcTool

object ModItems : HollowRegistry(HollowEngine.MODID) {
    val NPC_TOOL by register("npc_tool", AutoModelType.HANDHELD) { NpcTool() }
}