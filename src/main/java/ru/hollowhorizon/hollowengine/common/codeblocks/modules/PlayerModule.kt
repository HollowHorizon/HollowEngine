package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players.*

object PlayerModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        categoryAfter(2, "Игроки", Color("4287F5"), "hollowengine:textures/gui/icons/players.svg") {
            block("Получить игрока", ::GetPlayerByNameBlock)
            block("Взаимодействует с мобом", ::PlayerInteractWithEntity)
            block("Взаимодействует с блоков", ::PlayerInteractWithBlock)
            block("Использует предмет", ::PlayerInteractWithItem)
            block("Изменить Режим игры", ::PlayerGameModeBlock)
            block("Выдать предмет", ::PlayerGiveItemBlock)
            block("Проверить наличие предмета", ::PlayerHasItemBlock)
            block("Удалить предмет", ::PlayerRemoveItemBlock)
            block("Получить координаты", ::GetPlayerPositionBlock)
            block("Телепортировать", ::PlayerTeleportBlock)
            block("Отправить сообщение", ::PlayerSendMessageBlock)
            block("Получить здоровье", ::PlayerHealthBlock)
            block("Получить макс. здоровье", ::PlayerMaxHealthBlock)
            block("Получить уровень опыта", ::PlayerGetExperienceBlock)
            block("Установить здоровье", ::PlayerSetHealthBlock)
            block("Установить опыт", ::PlayerAddExperienceBlock)
            block("Исцелить", ::PlayerHealBlock)
            block("Нанести урон", ::PlayerHurtBlock)
        }
    }
}