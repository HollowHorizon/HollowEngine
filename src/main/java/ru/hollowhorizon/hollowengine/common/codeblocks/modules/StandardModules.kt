package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockEntry
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CallCustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events.OnEventBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events.OnStartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players.OnPlayerDeathBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.players.OnPlayerJoinBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.types.GetPlayerByNameBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.walk

object StandardModules {
    private val icons: (String) -> String = { "hollowengine:textures/gui/icons/$it.svg" }

    val General: BlockModule = {
        category("Основные", Color("A666EA"), icons("general")) {
            block("Вывод") { PrintBlock() }
            block("Ждать") { DelayBlock() }
            block("Выполнить команду") { ExecuteCommandBlock() }
        }
    }

    val Math: BlockModule = {
        category("Математика", Color("58B2EA"), icons("math")) {
            block("Операция") { MathBlock() }
            block("Случайное число") { RandomNumberBlock() }
        }
    }

    val Logic: BlockModule = {
        category("Логика", Color("1DB07D"), icons("logic")) {
            block("Если/Иначе") { IfElseBlock() }
            block("Если") { IfBlock() }
            blockWithColor("Сравнение", Color("3C44A0")) { CompareBlock() }
            blockWithColor("Логические операторы", Color("3C44A0")) { LogicBlock() }
            block("Не") { NotBlock() }
            block("Тест") { TestBlock() }
        }
    }

    val Types: BlockModule = {
        category("Типы данных", Color("F3BD3E"), icons("types")) {
            block("Строка") { StringValueBlock("") }
            block("Число") { NumberBlock() }
            block("Логический тип") { BoolBlock() }
            block("Координаты") { PositionBlock() }

            block("Получить игрока", ::GetPlayerByNameBlock)
        }
    }

    val Variables: BlockModule = {
        category("Переменные", Color("7248DD"), icons("variables")) {
            block("Присвоить") { SetVarBlock("") }
            block("Получить") { GetVarBlock("") }

            dynamicBlocks {
                rootBlocks.flatMap { it.walk() }.filterIsInstance<SetVarBlock>()
                    .filter { it.varName.isNotEmpty() }
                    .map {
                        BlockEntry(
                            "Получить ${it.varName}",
                            null,
                            { GetVarInlineBlock(it.varName).also { it.color = Color("7248DD") } },
                            GetVarInlineBlock::class
                        )
                    }
            }
        }
    }

    val Functions: BlockModule = {
        category("Функции", Color("EA6A5A"), icons("autocomplete_method")) {
            block("Создать функцию") { CustomBlock() }

            dynamicBlocks {
                rootBlocks.filterIsInstance<CustomBlock>().filter { it.function.isNotEmpty() }.map {
                    BlockEntry(
                        "Вызвать ${it.function}",
                        null,
                        { CallCustomBlock(it.function).also { it.color = Color("EA6A5A") } },
                        CallCustomBlock::class
                    )
                }
            }
        }
    }

    val Events: BlockModule = {
        category("События", Color("C94072"), icons("events")) {
            block("При запуске") { OnStartBlock() }
            block("При событии") { OnEventBlock() }
            block("Отправить событие") { SendEventBlock("") }

            block("При входе игрока") { OnPlayerJoinBlock() }
            block("При смерти игрока") { OnPlayerDeathBlock() }
        }
    }

    val Loops: BlockModule = {
        category("Циклы", Color("EB903F"), icons("loops")) {
            block("Пока") { WhileBlock() }
            block("Повторить") { RepeatBlock() }
        }
    }

    val AllBasics: BlockModule = {
        include(General)
        include(Events)
        include(Logic)
        include(Loops)
        include(Math)
        include(Variables)
        include(Functions)
        include(Types)
    }
}