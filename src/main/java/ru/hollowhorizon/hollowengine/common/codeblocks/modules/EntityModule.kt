package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity.EntityAddEffectBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity.EntityRemoveEffectBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity.EntityAngleBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity.EntityPickBlock

object EntityModule: BlockModule {
    override fun BlockCategoryBuilder.build() {
        category("Сущности", Color("1a8a01"), "hollowengine:textures/gui/icons/npcs.svg") {
            block("Рейкастинг") { EntityPickBlock() }
            block("Угол между") { EntityAngleBlock() }
            block("Добавить эффект") { EntityAddEffectBlock() }
            block("Убрать эффект") { EntityRemoveEffectBlock() }
        }
    }
}