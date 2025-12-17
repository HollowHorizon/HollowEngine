package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players.*

object PlayerModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        categoryAfter(2, "Игроки", Color("4287F5"), "hollowengine:textures/gui/icons/players.svg") {
            block("Взаимодействует с мобом", ::PlayerInteractWithEntity)
            block("Взаимодействует с блоков", ::PlayerInteractWithBlock)
            block("Использует предмет", ::PlayerInteractWithItem)
            block("Изменить Режим игры", ::PlayerGameModeBlock)
            block("Получить Режим игры", ::PlayerCheckGamemodeBlock)
            block("Выдать предмет", ::PlayerGiveItemBlock)
            block("Проверить наличие предмета", ::PlayerHasItemBlock)
            block("Удалить предмет", ::PlayerRemoveItemBlock)
            block("Получить координаты", ::GetPlayerPositionBlock)
            block("Телепортировать", ::PlayerTeleportBlock)
            block("Отправить сообщение", ::PlayerSendMessageBlock)
            block("Получить здоровье", ::PlayerHealthBlock)
            block("Получить макс. здоровье", ::PlayerMaxHealthBlock)
            block("Получить уровень опыта", ::GetPlayerXpLevelsBlock)
            block("Получить очки опыта", ::GetPlayerXpPointsBlock)
            block("Установить здоровье", ::PlayerSetHealthBlock)
            block("Выдать опыт", ::PlayerGiveXpPointsBlock)
            block("Выдать уровень", ::PlayerGiveXpLevelsBlock)
            block("Забрать опыт", ::PlayerRemoveXpLevelsBlock)
            block("Закрыть интерфейс", ::PlayerCloseGuiBlock)
            block("Исцелить", ::PlayerHealBlock)
            block("Нанести урон", ::PlayerHurtBlock)
            block("Знает ли рецепт", ::PlayerHasRecipeBlock)
            block("Задать уровень сытости", ::PlayerSetFoodBlock)
            block("Задать уровень насыщения", ::PlayerSetSaturationBlock)
            block("Увеличить истощение", ::PlayerAddExhaustionBlock)
            block("Получить уровень сытости", ::PlayerGetFoodBlock)
            block("Получить уровень насыщения", ::PlayerGetSaturationBlock)
        }
    }
}