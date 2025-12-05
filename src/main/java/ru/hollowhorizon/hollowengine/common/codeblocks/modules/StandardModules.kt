package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.*

object StandardModules {
    val General: BlockModule = {
        block("Вывод") { PrintBlock() }
        block("Ждать") { DelayBlock() }
    }

    val Math: BlockModule = {
        category("Математика") {
            block("Операция") { MathBlock() }
        }
    }

    val Logic: BlockModule = {
        category("Логика") {
            block("Сравнение") { LogicBlock() }
        }
    }

    val Types: BlockModule = {
        category("Типы данных") {
            block("Строка") { StringValueBlock("") }
            block("Число") { NumberBlock() }
            block("Логический тип") { BoolBlock() }
        }
    }

    val Variables: BlockModule = {
        category("Переменные") {
            block("Присвоить") { SetVarBlock("") }
            block("Получить") { GetVarBlock("") }
        }
    }

    val Events: BlockModule = {
        block("Передать") { SendEventBlock("") }
    }

    val Loops: BlockModule = {
        category("Циклы") {
            block("Пока") { WhileBlock() }
        }
    }

    val AllBasics: BlockModule = {
        include(General)
        include(Variables)
        include(Logic)
        include(Loops)
        include(Math)
        include(Events)
        include(Types)
    }
}