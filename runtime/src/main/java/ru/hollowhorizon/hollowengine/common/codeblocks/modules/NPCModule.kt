package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockEntry
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc.*
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath

object NPCModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        categoryAfter(2, "hollowengine.gui.codeblocks.category.npcs".lang, icons.NPCS) {
            block("hollowengine.gui.codeblocks.block.spawn_npc".lang, ::SpawnNpcBlock)
            block("hollowengine.gui.codeblocks.block.despawn_npc".lang, ::DespawnNpcBlock)

            block("hollowengine.gui.codeblocks.block.spawn_prefab".lang, ::SpawnNpcPrefabBlock)

            block("hollowengine.gui.codeblocks.block.play_animation".lang, ::NpcPlayAnimationBlock)
            block("hollowengine.gui.codeblocks.block.stop_animation".lang, ::NpcStopAnimationBlock)

            dynamicBlocks {
                val prefabsDir = DirectoryManager.HOLLOW_ENGINE.resolve("prefabs").toFile()
                if (!prefabsDir.exists()) return@dynamicBlocks emptyList()

                prefabsDir.walk()
                    .filter { it.isFile && it.name.endsWith(".entity.prefab") }
                    .map { file ->
                        val readablePath = file.toReadablePath()
                        val name = file.name.removeSuffix(".entity.prefab")
                        BlockEntry(
                            "hollowengine.gui.codeblocks.block.spawn_prefab_named".lang.format(name),
                            icons.NPCS,
                            { SpawnNpcPrefabBlock(readablePath) },
                            SpawnNpcPrefabBlock::class
                        )
                    }
                    .toList()
            }

            // --- AI и поведение ---
            block("hollowengine.gui.codeblocks.block.npc_move".lang, ::NpcMoveBlock)
            block("hollowengine.gui.codeblocks.block.npc_look".lang, ::NpcLookBlock)
            block("hollowengine.gui.codeblocks.block.npc_say".lang, ::NpcSayBlock)
            block("hollowengine.gui.codeblocks.block.npc_interact".lang, ::NpcInteractBlock)
            block("hollowengine.gui.codeblocks.block.npc_drop_item".lang, ::NpcDropItemBlock)

            // --- Цели ---
            block("hollowengine.gui.codeblocks.block.set_target".lang, ::NpcSetTargetBlock)
            block("hollowengine.gui.codeblocks.block.clear_target".lang, ::NpcClearTargetBlock)
            block("hollowengine.gui.codeblocks.block.distance_to".lang, ::NpcDistanceToBlock)

            // --- Свойства ---
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