package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import ru.hollowhorizon.hollowengine.client.utils.lang
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockEntry
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components.TextComponentBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components.TextMergerBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CallCustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events.OnEventBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events.OnStartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.items.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.math.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.nbt.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players.OnPlayerDeathBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players.OnPlayerJoinBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players.PlayerSelectedBlockPopupBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players.PlayerSelectedItemPopupBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.types.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.*
import ru.hollowhorizon.hollowengine.common.codeblocks.walk
import ru.hollowhorizon.hollowengine.generated.Assets

@PublishedApi
internal val icons = Assets.Hollowengine.Textures.Gui.Icons

object StandardModules {
    val General: BlockModule = {
        category("hollowengine.gui.codeblocks.category.general".lang, icons.GENERAL) {
            block("hollowengine.gui.codeblocks.block.print".lang) { PrintBlock() }
            block("hollowengine.gui.codeblocks.block.delay".lang) { DelayBlock() }
            block("hollowengine.gui.codeblocks.block.execute_command".lang) { ExecuteCommandBlock() }
        }
    }

    val Math: BlockModule = {
        category("hollowengine.gui.codeblocks.category.math".lang, icons.MATH) {
            block("hollowengine.gui.codeblocks.block.math_operation".lang) { MathBlock() }
            block("hollowengine.gui.codeblocks.block.random".lang) { RandomNumberBlock() }
            block("hollowengine.gui.codeblocks.block.trigonometry".lang) { TrigonometryBlock() }

            block("hollowengine.gui.codeblocks.block.distance_vec3".lang) { DistanceToBlock() }
            block("hollowengine.gui.codeblocks.block.vector_length".lang) { VectorLengthBlock() }
            block("hollowengine.gui.codeblocks.block.normalize_vector".lang) { NormalizeVectorBlock() }
            block("hollowengine.gui.codeblocks.block.vector_multiply".lang) { VectorMultiplyScalarBlock() }
            block("hollowengine.gui.codeblocks.block.get_x".lang) { VectorGetXBlock() }
            block("hollowengine.gui.codeblocks.block.get_y".lang) { VectorGetYBlock() }
            block("hollowengine.gui.codeblocks.block.get_z".lang) { VectorGetZBlock() }

            block("hollowengine.gui.codeblocks.block.pi".lang) { PiBlock() }
            block("hollowengine.gui.codeblocks.block.e".lang) { EBlock() }
        }
    }

    val Logic: BlockModule = {
        category("hollowengine.gui.codeblocks.category.logic".lang, icons.LOGIC) {
            block("hollowengine.gui.codeblocks.block.if_else".lang) { IfElseBlock() }
            block("hollowengine.gui.codeblocks.block.if_block".lang) { IfBlock() }
            block("hollowengine.gui.codeblocks.block.compare".lang) { CompareBlock() }
            block("hollowengine.gui.codeblocks.block.logic".lang) { LogicBlock() }
            block("hollowengine.gui.codeblocks.block.not".lang) { NotBlock() }
            block("hollowengine.gui.codeblocks.block.test".lang) { TestBlock() }
        }
    }

    val Types: BlockModule = {
        category("hollowengine.gui.codeblocks.category.types".lang, icons.TYPES) {
            block("hollowengine.gui.codeblocks.block.string".lang) { StringValueBlock("") }
            block("hollowengine.gui.codeblocks.block.number".lang) { NumberBlock() }
            block("hollowengine.gui.codeblocks.block.boolean".lang) { BoolBlock() }
            block("hollowengine.gui.codeblocks.block.position".lang) { PositionBlock() }
            block("hollowengine.gui.codeblocks.block.block_pos".lang) { BlockPosBlock() }

            block("hollowengine.gui.codeblocks.block.selected_item_popup".lang) { PlayerSelectedItemPopupBlock() }
            block("hollowengine.gui.codeblocks.block.selected_block_popup".lang) { PlayerSelectedBlockPopupBlock() }

            category("hollowengine.gui.codeblocks.category.items".lang, icons.TYPES) {
                block("hollowengine.gui.codeblocks.block.item_is_empty".lang) { ItemStackIsEmptyBlock() }
                block("hollowengine.gui.codeblocks.block.item_get_count".lang) { ItemStackGetCountBlock() }
                block("hollowengine.gui.codeblocks.block.item_get_max_stack".lang) { ItemStackGetMaxStackSizeBlock() }
                block("hollowengine.gui.codeblocks.block.item_is_damageable".lang) { ItemStackIsDamageableBlock() }
                block("hollowengine.gui.codeblocks.block.item_get_damage".lang) { ItemStackGetDamageBlock() }
                block("hollowengine.gui.codeblocks.block.item_get_max_damage".lang) { ItemStackGetMaxDamageBlock() }
                block("hollowengine.gui.codeblocks.block.item_get_durability".lang) { ItemStackGetDurabilityBlock() }
                block("hollowengine.gui.codeblocks.block.item_are_items_equal".lang) { ItemStackAreItemsEqualBlock() }
                block("hollowengine.gui.codeblocks.block.item_are_stacks_equal".lang) { ItemStackAreStacksEqualBlock() }
                block("hollowengine.gui.codeblocks.block.item_is_enchanted".lang) { ItemStackIsEnchantedBlock() }
                block("hollowengine.gui.codeblocks.block.item_has_enchant".lang) { ItemStackHasEnchantBlock() }
                block("hollowengine.gui.codeblocks.block.item_get_enchant_level".lang) { ItemStackGetEnchantLevelBlock() }
                block("hollowengine.gui.codeblocks.block.item_is_food".lang) { ItemStackIsFoodBlock() }
                block("hollowengine.gui.codeblocks.block.item_get_rarity".lang) { ItemStackGetRarityBlock() }
                block("hollowengine.gui.codeblocks.block.item_get_id".lang) { ItemStackGetIdBlock() }

                block("hollowengine.gui.codeblocks.block.item_has_tag".lang) { ItemStackHasItemTagBlock() }

                block("hollowengine.gui.codeblocks.block.item_get_tag".lang) { ItemStackGetNbtBlock() }
                block("hollowengine.gui.codeblocks.block.item_set_tag".lang) { ItemStackSetNbtBlock() }
                block("hollowengine.gui.codeblocks.block.item_clear_tag".lang) { ItemStackClearNbtBlock() }
            }

            category("hollowengine.gui.codeblocks.category.nbt".lang, icons.TYPES) {
                block("hollowengine.gui.codeblocks.block.nbt_new_compound".lang) { NbtNewCompoundBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_new_list".lang) { NbtNewListBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_parse".lang) { NbtParseCompoundBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_to_snbt".lang) { NbtToSnbtBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_contains".lang) { NbtContainsKeyBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_remove".lang) { NbtRemoveKeyBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_merge".lang) { NbtMergeBlock() }

                block("hollowengine.gui.codeblocks.block.nbt_get_string".lang) { NbtGetStringBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_get_int".lang) { NbtGetIntBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_get_boolean".lang) { NbtGetBooleanBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_get_compound".lang) { NbtGetCompoundBlock() }

                block("hollowengine.gui.codeblocks.block.nbt_set_string".lang) { NbtSetStringBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_set_int".lang) { NbtSetIntBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_set_boolean".lang) { NbtSetBooleanBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_set_compound".lang) { NbtSetCompoundBlock() }

                block("hollowengine.gui.codeblocks.block.nbt_list_size".lang) { NbtListSizeBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_list_get_string".lang) { NbtListGetStringBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_list_get_compound".lang) { NbtListGetCompoundBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_list_add_string".lang) { NbtListAddStringBlock() }
                block("hollowengine.gui.codeblocks.block.nbt_list_remove".lang) { NbtListRemoveBlock() }
            }

            block("hollowengine.gui.codeblocks.block.get_player_by_name".lang, ::GetPlayerByNameBlock)

            block("hollowengine.gui.codeblocks.block.text_component".lang) { TextComponentBlock() }
            block("hollowengine.gui.codeblocks.block.text_merger".lang) { TextMergerBlock() }

            category("hollowengine.gui.codeblocks.category.worlds".lang, icons.WORLD) {
                block("hollowengine.gui.codeblocks.block.get_overworld".lang) { GetOverworldBlock() }
                block("hollowengine.gui.codeblocks.block.get_nether".lang) { GetNetherBlock() }
                block("hollowengine.gui.codeblocks.block.get_end".lang) { GetTheEndBlock() }
            }
        }
    }

    val Variables: BlockModule = {
        category("hollowengine.gui.codeblocks.category.variables".lang, icons.VARIABLES) {
            category("hollowengine.gui.codeblocks.category.local".lang, icons.VARIABLES) {
                block("hollowengine.gui.codeblocks.block.set_var".lang) { SetVarBlock("") }
                block("hollowengine.gui.codeblocks.block.get_var".lang) { GetVarBlock("") }
                block("hollowengine.gui.codeblocks.block.event_output_var".lang) { EventOutputVariableBlock("") }

                dynamicBlocks {
                    rootBlocks.flatMap { it.walk() }.filterIsInstance<LocalVariableDeclaration>()
                        .filter { it.variableName.isNotEmpty() }
                        .distinctBy { it.variableName }
                        .map {
                            BlockEntry(
                                "hollowengine.gui.codeblocks.block.get_var_named".lang.format(it.variableName),
                                null,
                                { GetVarInlineBlock(it.variableName) },
                                GetVarInlineBlock::class
                            )
                        }
                }
            }
        }
    }

    val Functions: BlockModule = {
        category("hollowengine.gui.codeblocks.category.functions".lang, icons.AUTOCOMPLETE_METHOD) {
            block("hollowengine.gui.codeblocks.block.create_function".lang) { CustomBlock() }

            dynamicBlocks {
                rootBlocks.filterIsInstance<CustomBlock>().filter { it.function.isNotEmpty() }.map {
                    BlockEntry(
                        "hollowengine.gui.codeblocks.block.call_function".lang.format(it.function),
                        null,
                        { CallCustomBlock(it.function) },
                        CallCustomBlock::class
                    )
                }
            }
        }
    }

    val Events: BlockModule = {
        category("hollowengine.gui.codeblocks.category.events".lang, icons.EVENTS) {
            block("hollowengine.gui.codeblocks.block.on_start".lang) { OnStartBlock() }
            block("On signal") { OnEventBlock() }
            block("Send signal") { SendEventBlock("") }
            block("Call signal") { CallEventBlock("") }

            block("On player join") { OnPlayerJoinBlock() }
            block("On player death") { OnPlayerDeathBlock() }
        }
    }

    val Stops: BlockModule = {
        category("hollowengine.gui.codeblocks.category.stops".lang, icons.STOP) {
            block("hollowengine.gui.codeblocks.block.stop".lang) { StopBlock() }
            block("hollowengine.gui.codeblocks.block.stop_if".lang) { StopIfBlock() }
        }
    }

    val Loops: BlockModule = {
        category("hollowengine.gui.codeblocks.category.loops".lang, icons.LOOPS) {
            block("hollowengine.gui.codeblocks.block.while_loop".lang) { WhileBlock() }
            block("hollowengine.gui.codeblocks.block.repeat".lang) { RepeatBlock() }
        }
    }

    val AllBasics: BlockModule = {
        include(General)
        include(Events)
        include(Stops)
        include(Logic)
        include(Loops)
        include(Math)
        include(Variables)
        include(Functions)
        include(Types)
    }
}


