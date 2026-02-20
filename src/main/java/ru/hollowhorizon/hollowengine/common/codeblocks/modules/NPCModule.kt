package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockEntry
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc.*
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager
import ru.hollowhorizon.hollowengine.common.files.DirectoryManager.toReadablePath

object NPCModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        categoryAfter(2, "НИПы", Color("7EB542"), icons.NPCS) {
            block("Создать", ::SpawnNpcBlock)
            block("Удалить", ::DespawnNpcBlock)

            block("Создать из префаба", ::SpawnNpcPrefabBlock)

            block("Запустить анимацию", ::NpcPlayAnimationBlock)
            block("Остановить анимацию", ::NpcStopAnimationBlock)

            dynamicBlocks {
                val prefabsDir = DirectoryManager.HOLLOW_ENGINE.resolve("prefabs").toFile()
                if (!prefabsDir.exists()) return@dynamicBlocks emptyList()

                prefabsDir.walk()
                    .filter { it.isFile && it.name.endsWith(".entity.prefab") }
                    .map { file ->
                        val readablePath = file.toReadablePath()
                        val name = file.name.removeSuffix(".entity.prefab")
                        BlockEntry(
                            "Создать $name",
                            icons.NPCS,
                            { SpawnNpcPrefabBlock(readablePath).also { it.color = Color("7EB542") } },
                            SpawnNpcPrefabBlock::class
                        )
                    }
                    .toList()
            }

            // --- AI и поведение ---
            block("Идти", ::NpcMoveBlock)
            block("Смотреть", ::NpcLookBlock)
            block("Сказать", ::NpcSayBlock)
            block("Взаимодействовать", ::NpcInteractBlock)
            block("Бросить предмет", ::NpcDropItemBlock)

            // --- Цели ---
            block("Установить цель атаки", ::NpcSetTargetBlock)
            block("Сбросить цель", ::NpcClearTargetBlock)
            block("Расстояние до цели", ::NpcDistanceToBlock)

            // --- Специфичное ---
            block("Телепортировать", ::NpcTeleportBlock)

            block("Получить здоровье", ::NpcHealthBlock)
            block("Получить макс. здоровье", ::NpcMaxHealthBlock)
            block("Получить модификатор скорости", ::NpcSpeedBlock)
            block("Установить здоровье", ::NpcSetHealthBlock)
            block("Исцелить НИПа", ::NpcHealBlock)
            block("Установить имя", ::NpcSetNameBlock)
        }
    }
}