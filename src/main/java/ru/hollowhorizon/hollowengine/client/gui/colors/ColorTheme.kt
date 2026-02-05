package ru.hollowhorizon.hollowengine.client.gui.colors

import de.fabmax.kool.modules.ui2.Dp
import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.scripting.theme.loadFont
import ru.hollowhorizon.hollowengine.common.utils.rl

object ColorTheme {
    object Fonts {
        val PT_SANS by lazy { loadFont("hollowengine:fonts/pt_sans.json".rl) }
        val MONOCRAFT by lazy { loadFont("hollowengine:fonts/monocraft.json".rl) }
        val HACK by lazy { loadFont("hollowengine:fonts/hack.json".rl) }
    }

    object UI {
        val BackgroundGeneral = Color("1E1F22")   // Фон Общий
        val BackgroundSecondary = Color("24272E") // Фон Вторичный
        val BackgroundElements = Color("31343D")  // Фон Элементов
        val BackgroundAccent = Color("5F6677")    // Фон Акцента
        val BackgroundDarker = Color("1E2127")
        val WhiteReplacement = Color("C4CBDA")    // Замена "Белому"

        val ForegroundSecondary = Color("2A2E35")
    }

    object GraphColors {
        val GridBackground = Color("181818") // Темный фон
        val GridLines = Color("252525")      // Чуть светлее линии
        val NodeBackground = Color("424242") // Серый фон узла
        val SelectionBorder = Color("D77F1C") // Оранжевая обводка

        // Типы узлов
        val StateEntry = Color("6BC872")     // Зеленый (Entry)
        val StateExit = Color("DB5C5C")      // Красный (Exit)
        val StateAny = Color("5BB2E8")       // Голубой (Any State)
        val StateDefault = Color("D77F1C")   // Оранжевый (Idle/Active)
        val StateNormal = Color("606060")    // Серый (Обычные стейты)

        val LinkColor = Color("A07040")      // Цвет стрелок (оранжево-коричневый)
    }

    object Accents {
        val Main = Color("D77F1C")          // Главный акцент
        val Success = Color("56C351")       // Запуск / успех
    }

    object Console {
        val Debug = Color("393D48")
        val Info = Color("7BBA2E")
        val Warning = Color("EBBC4D")
        val Error = Color("DB5C5C")
        val OutputTime = Color("9B8DFF")
    }

    object Icons {
        val NPC = Color("6BC872")
        val Data = Color("60C0B5")
        val Image = Color("5BB2E8")
        val Camera = Color("548AF7")
        val Script = Color("9471FF")
        val Archives = Color("FF954A")
        val Blocks = Color("E5986C")
        val Assets = Color("A1DF55")
    }

    object CodeWindow {
        val LineNumbers = Color("676C77")   // Нумерация строк
        val Libraries = Color("8D93A1")     // Библиотеки
        val MainCode = Color("D7DFEF")      // Основной код
        val Calls = Color("FF9B61")         // Вызовы
        val Methods = Color("E4B348")       // Методы
        val AccentCode = Color("E590E4")    // Акцентный код
        val Selection = Color("3399FF")
    }

    object Blocks {
        val Loops = Color("EB903F")         // Циклы
        val DataTypes = Color("F3BD3E")     // Типы данных
        val NPC = Color("7EB542")           // NPC
        val LogicTeal = Color("1DB07D")     // Логика (зеленый оттенок)
        val Math = Color("58B2EA")          // Математика
        val LogicBlue = Color("3C44A0")     // Логика (синий оттенок)
        val Variables = Color("7248DD")     // Переменные
        val MainCore = Color("A666EA")      // Основные (Core)
        val Events = Color("C94072")        // События
    }
}

object Dimensions {
    var PaddingSmall = Dp(2f)
    var PaddingNormal = Dp(4f)
    var PaddingMedium = Dp(8f)
    var PaddingHuge = Dp(16f)
    var PaddingLarge = Dp(24f)
    var PaddingExtraLarge = Dp(32f)

    var FontNormal = 16f
    var FontSmall = 12f
    var FontLarge = 20f
}

val Dimensions.PaddingLargeSpacing get() = PaddingLarge + PaddingNormal