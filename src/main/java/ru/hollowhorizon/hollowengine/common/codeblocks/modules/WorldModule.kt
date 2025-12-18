package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.world.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.world.schematics.PlaceSchematicBlock

object WorldModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        categoryAfter(0, "Мир", Color("ba9307"), "hollowengine:textures/gui/icons/world.svg") {
            block("Установить блок", ::SetBlockBlock)
            block("Обновить блок", ::UpdateBlockBlock)
            block("Сломать блок", ::RemoveBlockBlock)
            block("Получить блок", ::GetBlockBlock)
            block("Повернуть блок", ::RotateBlockBlock)
            block("Призвать сущность", ::SpawnEntityBlock)

            block("Видно ли небо?", ::HasSkyAtBlock)
            block("Получить время", ::GetTimeBlock)
            block("Установить время", ::SetTimeBlock)
            block("Получить погоду", ::GetWeatherBlock)
            block("Изменить погоду", ::SetWeatherBlock)

            category("Структуры", Color("6234c7"), "hollowengine:textures/gui/icons/structures.svg") {
                block("Разместить схематику", ::PlaceSchematicBlock)
            }
        }
    }
}