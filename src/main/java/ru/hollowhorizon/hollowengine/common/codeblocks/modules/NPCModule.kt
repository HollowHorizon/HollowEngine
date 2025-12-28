package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc.*

object NPCModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        categoryAfter(2, "НИПы", Color("7EB542"), icons.NPCS) {
            block("Создать", ::SpawnNpcBlock)
            block("Удалить", ::DespawnNpcBlock)

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