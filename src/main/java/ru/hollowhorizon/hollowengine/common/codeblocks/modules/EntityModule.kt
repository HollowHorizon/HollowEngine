package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockCategoryBuilder
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity.attributes.EntityGetBaseSpeed
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.entity.attributes.EntitySetBaseSpeed

object EntityModule : BlockModule {
    override fun BlockCategoryBuilder.build() {
        category("hollowengine.gui.codeblocks.category.entities".lang, icons.NPCS) {
            // --- Свойства и Геттеры ---
            block("hollowengine.gui.codeblocks.block.get_position".lang) { EntityGetPosition() }
            block("hollowengine.gui.codeblocks.block.get_name".lang) { EntityGetName() }
            block("hollowengine.gui.codeblocks.block.get_look_angle".lang) { EntityGetLookAngle() }
            block("hollowengine.gui.codeblocks.block.get_main_hand".lang) { EntityGetMainHandItem() }
            block("hollowengine.gui.codeblocks.block.get_off_hand".lang) { EntityGetOffHandItem() }
            block("hollowengine.gui.codeblocks.block.get_equipment".lang) { EntityGetEquipmentBlock() }
            block("hollowengine.gui.codeblocks.block.get_speed_modifier".lang) { EntityGetBaseSpeed() }

            // --- Проверки (Booleans) ---
            block("hollowengine.gui.codeblocks.block.is_alive".lang) { EntityIsAlive() }
            block("hollowengine.gui.codeblocks.block.is_aggressive".lang) { EntityIsAggressive() }
            block("hollowengine.gui.codeblocks.block.is_attackable".lang) { EntityIsAttackable() }
            block("hollowengine.gui.codeblocks.block.is_invisible".lang) { EntityIsInvisible() }
            block("hollowengine.gui.codeblocks.block.is_invulnerable".lang) { EntityIsInvulnerable() }

            // Проверки окружения
            block("hollowengine.gui.codeblocks.block.is_swimming".lang) { EntityIsSwimming() }
            block("hollowengine.gui.codeblocks.block.is_underwater".lang) { EntityIsUnderwater() }
            block("hollowengine.gui.codeblocks.block.is_on_fire".lang) { EntityIsOnFire() }
            block("hollowengine.gui.codeblocks.block.is_in_lava".lang) { EntityIsInLava() }

            // Проверки позы
            block("hollowengine.gui.codeblocks.block.is_standing".lang) { EntityIsStanding() }
            block("hollowengine.gui.codeblocks.block.is_crouching".lang) { EntityIsCrouching() }
            block("hollowengine.gui.codeblocks.block.is_sitting".lang) { EntityIsSitting() }
            block("hollowengine.gui.codeblocks.block.is_running".lang) { EntityIsRunning() }
            block("hollowengine.gui.codeblocks.block.is_sleeping".lang) { EntityIsSleeping() }

            // --- Действия ---
            block("hollowengine.gui.codeblocks.block.entity_hurt".lang) { EntityHurtBlock() }
            block("hollowengine.gui.codeblocks.block.clear_fire".lang) { EntityClearFire() }
            block("hollowengine.gui.codeblocks.block.remove_entity".lang) { RemoveEntityBlock() }
            block("hollowengine.gui.codeblocks.block.push".lang) { PushEntityBlock() }
            block("hollowengine.gui.codeblocks.block.swing_main".lang) { SwingMainHandBlock() }
            block("hollowengine.gui.codeblocks.block.swing_off".lang) { SwingOffHandBlock() }
            block("hollowengine.gui.codeblocks.block.set_speed_modifier".lang) { EntitySetBaseSpeed() }

            // --- Старое ---
            block("hollowengine.gui.codeblocks.block.raycast".lang) { EntityPickBlock() }
        }
    }
}