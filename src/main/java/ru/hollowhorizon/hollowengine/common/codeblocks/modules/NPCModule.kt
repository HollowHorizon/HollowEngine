package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.npc.*

object NPCModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        categoryAfter(2, "НИПы", Color("7EB542"), "hollowengine:textures/gui/icons/npcs.svg") {
            block("Создать", ::SpawnNpcBlock)
            block("Идти", ::NpcMoveBlock)
            block("Смотреть", ::NpcLookBlock)
            block("Сказать", ::NpcSayBlock)
            block("Взаимодействовать", ::NpcInteractBlock)
            block("Удалить", ::DespawnNpcBlock)
            block("Бросить предмет", ::NpcDropItemBlock)
        }
    }
}