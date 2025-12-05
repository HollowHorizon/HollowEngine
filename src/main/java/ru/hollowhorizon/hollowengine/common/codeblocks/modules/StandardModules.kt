package ru.hollowhorizon.hollowengine.common.codeblocks.modules

import de.fabmax.kool.util.Color
import de.fabmax.kool.util.MdColor
import ru.hollowhorizon.hollowengine.common.codeblocks.BlockModule
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.*
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events.OnEventBlock
import ru.hollowhorizon.hollowengine.common.codeblocks.blocks.events.OnStartBlock

object StandardModules {
    val General: BlockModule = {
        block("Вывод") { PrintBlock() }
        block("Ждать") { DelayBlock() }
        block("Команда (minecraft)") { ExecuteCommandBlock() }
    }

    val Math: BlockModule = {
        category("Математика", MdColor.BLUE) {
            block("Операция") { MathBlock() }
        }
    }

    val Logic: BlockModule = {
        category("Логика", MdColor.TEAL) {
            block("Если/Иначе") { IfElseBlock() }
            block("Если") { IfBlock() }
            block("Сравнение") { LogicBlock() }
            block("Тест") { TestBlock() }
            block("Передать") { SendEventBlock("") }
        }
    }

    val Types: BlockModule = {
        category("Типы данных", MdColor.AMBER) {
            block("Строка") { StringValueBlock("") }
            block("Число") { NumberBlock() }
            block("Логический тип") { BoolBlock() }
            block("Координаты") { PositionBlock() }
        }
    }

    val Variables: BlockModule = {
        category("Переменные", Color("680bbf")) {
            block("Присвоить") { SetVarBlock("") }
            block("Получить") { GetVarBlock("") }
        }
    }

    val Events: BlockModule = {
        category("События", Color("ffa70f")) {
            block("При запуске") { OnStartBlock() }
            block("При событии") { OnEventBlock() }
        }
    }

    val Loops: BlockModule = {
        category("Циклы", MdColor.ORANGE) {
            block("Пока") { WhileBlock() }
            block("Повторить") { RepeatBlock() }
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