package ru.hollowhorizon.hollowengine.common.registry

import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hc.common.registry.ObjectConfig
import ru.hollowhorizon.hollowengine.common.items.CameraItem
import ru.hollowhorizon.hollowengine.common.items.NpcTool
import ru.hollowhorizon.hollowengine.common.items.StoryTellerDimItem

object ModItems : HollowRegistry() {
    val STORYTELLER_DIM_TELEPORTER by register("storyteller_dim_teleporter", ::StoryTellerDimItem)
    val CAMERA by register(ObjectConfig(name = "camera", autoModel = false), ::CameraItem)
    val NPC_TOOL by register("npc_tool", ::NpcTool)
}