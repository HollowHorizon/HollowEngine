package ru.hollowhorizon.hollowengine.common.registry

import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.common.registry.HollowRegistry
import ru.hollowhorizon.hollowengine.HollowEngine
import ru.hollowhorizon.hollowengine.common.items.StoryTellerDimItem

object ModItems : HollowRegistry() {
    val STORYTELLER_DIM_TELEPORTER by register("${HollowEngine.MODID}:storyteller_dim_teleporter".rl) { StoryTellerDimItem() }
}