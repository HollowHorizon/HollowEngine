package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import ru.hollowhorizon.hollowengine.common.codeblocks.BlockEntry
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components.TextComponentBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components.TextMergerBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CallCustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events.OnStartBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.math.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.types.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.variables.*
import ru.hollowhorizon.hollowengine.common.codeblocks.walk
import ru.hollowhorizon.hollowengine.generated.Assets

@PublishedApi
internal val icons = Assets.Hollowengine.Textures.Gui.Icons

object StandardModules {
    val General: BlockModule = {
        category("Основные", icons.GENERAL) {
            block("Вывод") { PrintBlock() }
            block("Ждать") { DelayBlock() }
            block("Выполнить команду") { ExecuteCommandBlock() }
        }
    }

    val Math: BlockModule = {
        category("Математика", icons.MATH) {
            block("Операция") { MathBlock() }
            block("Случайное число") { RandomNumberBlock() }
            block("Тригонометрия") { TrigonometryBlock() }

            block("Расстояние между (Vec3)") { DistanceToBlock() }
            block("Длина вектора") { VectorLengthBlock() }
            block("Нормализовать вектор") { NormalizeVectorBlock() }
            block("Умножить вектор на число") { VectorMultiplyScalarBlock() }
            block("Получить X") { VectorGetXBlock() }
            block("Получить Y") { VectorGetYBlock() }
            block("Получить Z") { VectorGetZBlock() }

            block("Число Пи") { PiBlock() }
            block("Экспонента") { EBlock() }
        }
    }

    val Logic: BlockModule = {
        category("Логика", icons.LOGIC) {
            block("Если/Иначе") { IfElseBlock() }
            block("Если") { IfBlock() }
            block("Сравнение") { CompareBlock() }
            block("Логические операторы") { LogicBlock() }
            block("Не") { NotBlock() }
            block("Тест") { TestBlock() }
        }
    }

    val Types: BlockModule = {
        category("Типы данных", icons.TYPES) {
            block("Строка") { StringValueBlock("") }
            block("Число") { NumberBlock() }
            block("Логический тип") { BoolBlock() }
            block("Координаты") { PositionBlock() }
            block("Координаты блока") { BlockPosBlock() }

            block("Получить игрока", ::GetPlayerByNameBlock)

            block("Текстовый компонент") { TextComponentBlock() }
            block("Объединить компоненты") { TextMergerBlock() }

            category("Миры", icons.WORLD) {
                block("Обычный мир") { GetOverworldBlock() }
                block("Незер") { GetNetherBlock() }
                block("Энд") { GetTheEndBlock() }
            }
        }
    }

    val Variables: BlockModule = {
        category("Переменные", icons.VARIABLES) {
            category("Локальные", icons.VARIABLES) {
                block("Присвоить") { SetVarBlock("") }
                block("Получить") { GetVarBlock("") }

                dynamicBlocks {
                    rootBlocks.flatMap { it.walk() }.filterIsInstance<SetVarBlock>()
                        .filter { it.variableName.isNotEmpty() }
                        .map {
                            BlockEntry(
                                "Получить ${it.variableName}",
                                null,
                                { GetVarInlineBlock(it.variableName) },
                                GetVarInlineBlock::class
                            )
                        }
                }
            }

            category("Глобальные", icons.VARIABLES) {
                block("Присвоить") { SetGlobalVarBlock("") }
                block("Получить") { GetGlobalVarBlock("") }

                dynamicBlocks {
                    rootBlocks.flatMap { it.walk() }.filterIsInstance<SetGlobalVarBlock>()
                        .filter { it.variableName.isNotEmpty() }
                        .map {
                            BlockEntry(
                                "Получить ${it.variableName}",
                                null,
                                { GetGlobalVarBlock(it.variableName) },
                                GetGlobalVarBlock::class
                            )
                        }
                }
            }

            category("Сущности", icons.VARIABLES) {
                block("Присвоить") { SetEntityVarBlock("") }
                block("Получить") { GetEntityVarBlock("") }

                dynamicBlocks {
                    rootBlocks.flatMap { it.walk() }.filterIsInstance<SetEntityVarBlock>()
                        .filter { it.varName.isNotEmpty() }
                        .map {
                            BlockEntry(
                                "Получить ${it.varName}",
                                null,
                                { GetEntityVarBlock(it.varName) },
                                GetEntityVarBlock::class
                            )
                        }
                }
            }
        }
    }

    val Functions: BlockModule = {
        category("Функции", icons.AUTOCOMPLETE_METHOD) {
            block("Создать функцию") { CustomBlock() }

            dynamicBlocks {
                rootBlocks.filterIsInstance<CustomBlock>().filter { it.function.isNotEmpty() }.map {
                    BlockEntry(
                        "Вызвать ${it.function}",
                        null,
                        { CallCustomBlock(it.function) },
                        CallCustomBlock::class
                    )
                }
            }
        }
    }

    val Events: BlockModule = {
        category("События", icons.EVENTS) {
            block("При запуске") { OnStartBlock() }
            //block("При событии") { OnEventBlock() }
            //block("Отправить событие") { SendEventBlock("") }

            //block("При входе игрока") { OnPlayerJoinBlock() }
            //block("При смерти игрока") { OnPlayerDeathBlock() }
        }
    }

    val Stops: BlockModule = {
        category("Завершения", icons.STOP) {
            block("Завершить скрипт") { StopBlock() }
            block("Завершить скрипт, если") { StopIfBlock() }
        }
    }

    val Loops: BlockModule = {
        category("Циклы", icons.LOOPS) {
            block("Пока") { WhileBlock() }
            block("Повторить") { RepeatBlock() }
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