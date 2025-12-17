package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players.*

object PlayerModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        categoryAfter(2, "Игроки", Color("4287F5"), "hollowengine:textures/gui/icons/players.svg") {
            block("Телепортировать") { PlayerTeleportBlock() }
            block("Установить точку возрождения") { PlayerSetRespawn() }
            block("Отправить сообщение") { PlayerSendMessageBlock() }
            block("Закрыть интерфейс") { PlayerCloseGuiBlock() }

            block("Выдать предмет") { PlayerGiveItemBlock() }
            block("Удалить предмет") { PlayerRemoveItemBlock() }
            block("Проверить наличие предмета") { PlayerHasItemBlock() }
            block("Знает ли рецепт") { PlayerHasRecipeBlock() }

            block("Задать сытость") { PlayerSetFoodBlock() }
            block("Задать насыщение") { PlayerSetSaturationBlock() }
            block("Добавить истощение") { PlayerAddExhaustionBlock() }
            block("Получить сытость") { PlayerGetFoodBlock() }
            block("Получить насыщение") { PlayerGetSaturationBlock() }

            block("Задать поглощение") { PlayerSetAbsorption() }
            block("Получить поглощение") { PlayerGetAbsorption() }
            block("Получить здоровье") { PlayerHealthBlock() }
            block("Получить макс. здоровье") { PlayerMaxHealthBlock() }
            block("Установить здоровье") { PlayerSetHealthBlock() }
            block("Исцелить") { PlayerHealBlock() }

            // --- Опыт ---
            block("Выдать опыт") { PlayerGiveXpPointsBlock() }
            block("Выдать уровень") { PlayerGiveXpLevelsBlock() }
            block("Забрать опыт") { PlayerRemoveXpLevelsBlock() }
            block("Получить уровень опыта") { GetPlayerXpLevelsBlock() }
            block("Получить очки опыта") { GetPlayerXpPointsBlock() }

            // --- Разное ---
            block("Изменить Режим игры") { PlayerGameModeBlock() }
            block("Получить Режим игры") { PlayerCheckGamemodeBlock() }
            block("Взаимодействует с мобом") { PlayerInteractWithEntity() }
            block("Взаимодействует с блоком") { PlayerInteractWithBlock() }
            block("Использует предмет") { PlayerInteractWithItem() }
        }
    }
}