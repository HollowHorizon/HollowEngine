package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc.*

object NPCModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        categoryAfter(2, "hollowengine.gui.codeblocks.category.npcs".lang, icons.NPCS) {
            block("hollowengine.gui.codeblocks.block.spawn_npc".lang, ::SpawnNpcBlock)
            block("hollowengine.gui.codeblocks.block.despawn_npc".lang, ::DespawnNpcBlock)

            block("hollowengine.gui.codeblocks.block.play_animation".lang, ::NpcPlayAnimationBlock)
            block("hollowengine.gui.codeblocks.block.stop_animation".lang, ::NpcStopAnimationBlock)

            // --- AI and behavior ---
            block("hollowengine.gui.codeblocks.block.npc_move".lang, ::NpcMoveBlock)
            block("hollowengine.gui.codeblocks.block.npc_look".lang, ::NpcLookBlock)
            block("hollowengine.gui.codeblocks.block.npc_say".lang, ::NpcSayBlock)
            block("hollowengine.gui.codeblocks.block.npc_interact".lang, ::NpcInteractBlock)
            block("hollowengine.gui.codeblocks.block.npc_drop_item".lang, ::NpcDropItemBlock)

            // --- Targets ---
            block("hollowengine.gui.codeblocks.block.set_target".lang, ::NpcSetTargetBlock)
            block("hollowengine.gui.codeblocks.block.clear_target".lang, ::NpcClearTargetBlock)
            block("hollowengine.gui.codeblocks.block.distance_to".lang, ::NpcDistanceToBlock)

            // --- Properties ---
            block("hollowengine.gui.codeblocks.block.npc_get_health".lang, ::NpcHealthBlock)
            block("hollowengine.gui.codeblocks.block.npc_get_max_health".lang, ::NpcMaxHealthBlock)
            block("hollowengine.gui.codeblocks.block.npc_get_speed".lang, ::NpcSpeedBlock)
            block("hollowengine.gui.codeblocks.block.npc_teleport".lang, ::NpcTeleportBlock)
            block("hollowengine.gui.codeblocks.block.npc_heal".lang, ::NpcHealBlock)
            block("hollowengine.gui.codeblocks.block.npc_hurt".lang, ::NpcHurtBlock)
            block("hollowengine.gui.codeblocks.block.npc_set_name".lang, ::NpcSetNameBlock)
        }
    }
}
