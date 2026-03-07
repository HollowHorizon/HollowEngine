package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity.PlayerGetInventoryItemBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players.*

object PlayerModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        categoryAfter(2, "hollowengine.gui.codeblocks.category.players".lang, icons.PLAYERS) {
            block("hollowengine.gui.codeblocks.block.teleport".lang) { PlayerTeleportBlock() }
            block("hollowengine.gui.codeblocks.block.set_respawn".lang) { PlayerSetRespawn() }
            block("hollowengine.gui.codeblocks.block.send_message".lang) { PlayerSendMessageBlock() }
            block("hollowengine.gui.codeblocks.block.close_gui".lang) { PlayerCloseGuiBlock() }

            block("hollowengine.gui.codeblocks.block.give_item".lang) { PlayerGiveItemBlock() }
            block("hollowengine.gui.codeblocks.block.has_recipe".lang) { PlayerHasRecipeBlock() }

            block("hollowengine.gui.codeblocks.block.get_inventory_item".lang) { PlayerGetInventoryItemBlock() }

            block("hollowengine.gui.codeblocks.block.set_food".lang) { PlayerSetFoodBlock() }
            block("hollowengine.gui.codeblocks.block.set_saturation".lang) { PlayerSetSaturationBlock() }
            block("hollowengine.gui.codeblocks.block.add_exhaustion".lang) { PlayerAddExhaustionBlock() }
            block("hollowengine.gui.codeblocks.block.get_food".lang) { PlayerGetFoodBlock() }
            block("hollowengine.gui.codeblocks.block.get_saturation".lang) { PlayerGetSaturationBlock() }

            block("hollowengine.gui.codeblocks.block.set_absorption".lang) { PlayerSetAbsorption() }
            block("hollowengine.gui.codeblocks.block.get_absorption".lang) { PlayerGetAbsorption() }
            block("hollowengine.gui.codeblocks.block.get_health".lang) { PlayerHealthBlock() }
            block("hollowengine.gui.codeblocks.block.get_max_health".lang) { PlayerMaxHealthBlock() }
            block("hollowengine.gui.codeblocks.block.set_health".lang) { PlayerSetHealthBlock() }
            block("hollowengine.gui.codeblocks.block.heal".lang) { PlayerHealBlock() }

            block("hollowengine.gui.codeblocks.block.give_xp".lang) { PlayerGiveXpPointsBlock() }
            block("hollowengine.gui.codeblocks.block.give_level".lang) { PlayerGiveXpLevelsBlock() }
            block("hollowengine.gui.codeblocks.block.remove_xp".lang) { PlayerRemoveXpLevelsBlock() }
            block("hollowengine.gui.codeblocks.block.get_xp_level".lang) { GetPlayerXpLevelsBlock() }
            block("hollowengine.gui.codeblocks.block.get_xp_points".lang) { GetPlayerXpPointsBlock() }

            block("hollowengine.gui.codeblocks.block.set_gamemode".lang) { PlayerGameModeBlock() }
            block("hollowengine.gui.codeblocks.block.get_gamemode".lang) { PlayerCheckGamemodeBlock() }
            block("hollowengine.gui.codeblocks.block.interact_entity".lang) { PlayerInteractWithEntity() }
            block("hollowengine.gui.codeblocks.block.interact_block".lang) { PlayerInteractWithBlock() }
        }
    }
}
