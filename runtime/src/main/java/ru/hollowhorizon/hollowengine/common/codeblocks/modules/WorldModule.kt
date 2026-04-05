package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.world.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.world.schematics.PlaceSchematicBlock

object WorldModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        categoryAfter(0, "hollowengine.gui.codeblocks.category.world".lang, icons.WORLD) {
            block("hollowengine.gui.codeblocks.block.set_block".lang, ::SetBlockBlock)
            block("hollowengine.gui.codeblocks.block.update_block".lang, ::UpdateBlockBlock)
            block("hollowengine.gui.codeblocks.block.remove_block".lang, ::RemoveBlockBlock)
            block("hollowengine.gui.codeblocks.block.get_block".lang, ::GetBlockBlock)
            block("hollowengine.gui.codeblocks.block.rotate_block".lang, ::RotateBlockBlock)
            block("hollowengine.gui.codeblocks.block.spawn_entity".lang, ::SpawnEntityBlock)

            block("hollowengine.gui.codeblocks.block.has_sky_at".lang, ::HasSkyAtBlock)
            block("hollowengine.gui.codeblocks.block.get_time".lang, ::GetTimeBlock)
            block("hollowengine.gui.codeblocks.block.set_time".lang, ::SetTimeBlock)
            block("hollowengine.gui.codeblocks.block.get_weather".lang, ::GetWeatherBlock)
            block("hollowengine.gui.codeblocks.block.set_weather".lang, ::SetWeatherBlock)

            category("hollowengine.gui.codeblocks.category.structures".lang, icons.STRUCTURES) {
                block("hollowengine.gui.codeblocks.block.place_schematic".lang, ::PlaceSchematicBlock)
            }
        }
    }
}