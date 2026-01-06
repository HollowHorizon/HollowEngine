package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockEntry
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components.TextComponentBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.components.TextMergerBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CallCustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.custom.CustomBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events.OnEventBlock
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
        category("Основные", Color("A666EA"), icons.GENERAL) {
            block("Вывод") { PrintBlock() }
            block("Ждать") { DelayBlock() }
            block("Выполнить команду") { ExecuteCommandBlock() }
        }
    }

    val Math: BlockModule = {
        category("Математика", Color("58B2EA"), icons.MATH) {
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
        category("Логика", Color("1DB07D"), icons.LOGIC) {
            block("Если/Иначе") { IfElseBlock() }
            block("Если") { IfBlock() }
            blockWithColor("Сравнение", Color("3C44A0")) { CompareBlock() }
            blockWithColor("Логические операторы", Color("3C44A0")) { LogicBlock() }
            block("Не") { NotBlock() }
            block("Тест") { TestBlock() }
        }
    }

    val Types: BlockModule = {
        category("Типы данных", Color("F3BD3E"), icons.TYPES) {
            block("Строка") { StringValueBlock("") }
            block("Число") { NumberBlock() }
            block("Логический тип") { BoolBlock() }
            block("Координаты") { PositionBlock() }
            block("Координаты блока") { BlockPosBlock() }

            block("Получить игрока", ::GetPlayerByNameBlock)

            block("Текстовый компонент") { TextComponentBlock() }
            block("Объединить компоненты") { TextMergerBlock() }

            category("Миры", Color("ba9307"), icons.WORLD) {
                block("Обычный мир") { GetOverworldBlock() }
                block("Незер") { GetNetherBlock() }
                block("Энд") { GetTheEndBlock() }
            }
        }
    }

    val Variables: BlockModule = {
        category("Переменные", Color("7248DD"), icons.VARIABLES) {
            category("Локальные", Color("7248DD"), icons.VARIABLES) {
                block("Присвоить") { SetVarBlock("") }
                block("Получить") { GetVarBlock("") }

                dynamicBlocks {
                    rootBlocks.flatMap { it.walk() }.filterIsInstance<SetVarBlock>()
                        .filter { it.variableName.isNotEmpty() }
                        .map {
                            BlockEntry(
                                "Получить ${it.variableName}",
                                null,
                                { GetVarInlineBlock(it.variableName).also { it.color = Color("7248DD") } },
                                GetVarInlineBlock::class
                            )
                        }
                }
            }

            category("Глобальные", Color("5e1f0d"), icons.VARIABLES) {
                block("Присвоить") { SetGlobalVarBlock("") }
                block("Получить") { GetGlobalVarBlock("") }

                dynamicBlocks {
                    rootBlocks.flatMap { it.walk() }.filterIsInstance<SetGlobalVarBlock>()
                        .filter { it.variableName.isNotEmpty() }
                        .map {
                            BlockEntry(
                                "Получить ${it.variableName}",
                                null,
                                { GetGlobalVarBlock(it.variableName).also { it.color = Color("7248DD") } },
                                GetGlobalVarBlock::class
                            )
                        }
                }
            }

            category("Сущности", Color("007a1d"), icons.VARIABLES) {
                block("Присвоить") { SetEntityVarBlock("") }
                block("Получить") { GetEntityVarBlock("") }

                dynamicBlocks {
                    rootBlocks.flatMap { it.walk() }.filterIsInstance<SetEntityVarBlock>()
                        .filter { it.varName.isNotEmpty() }
                        .map {
                            BlockEntry(
                                "Получить ${it.varName}",
                                null,
                                { GetEntityVarBlock(it.varName).also { it.color = Color("7248DD") } },
                                GetEntityVarBlock::class
                            )
                        }
                }
            }
        }
    }

    val Functions: BlockModule = {
        category("Функции", Color("EA6A5A"), icons.AUTOCOMPLETE_METHOD) {
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
        category("События", Color("C94072"), icons.EVENTS) {
            block("При запуске") { OnStartBlock() }
            block("При событии") { OnEventBlock() }
            block("Отправить событие") { SendEventBlock("") }

            //block("При входе игрока") { OnPlayerJoinBlock() }
            //block("При смерти игрока") { OnPlayerDeathBlock() }
        }
    }

    val Loops: BlockModule = {
        category("Циклы", Color("EB903F"), icons.LOOPS) {
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