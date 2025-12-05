package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import de.fabmax.kool.util.MdColor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.*

object StandardModules {
    val General: BlockModule = {
        block("Вывод") { PrintBlock() }
        block("Ждать") { DelayBlock() }
    }

    val Math: BlockModule = {
        category("Математика", MdColor.BLUE) {
            block("Операция") { MathBlock() }
        }
    }

    val Logic: BlockModule = {
        category("Логика", MdColor.INDIGO) {
            block("Сравнение") { LogicBlock() }
        }
    }

    val Types: BlockModule = {
        category("Типы данных", MdColor.AMBER) {
            block("Строка") { StringValueBlock("") }
            block("Число") { NumberBlock() }
            block("Логический тип") { BoolBlock() }
        }
    }

    val Variables: BlockModule = {
        category("Переменные", MdColor.DEEP_ORANGE) {
            block("Присвоить") { SetVarBlock("") }
            block("Получить") { GetVarBlock("") }
        }
    }

    val Events: BlockModule = {
        block("Передать") { SendEventBlock("") }
    }

    val Loops: BlockModule = {
        category("Циклы", MdColor.ORANGE) {
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