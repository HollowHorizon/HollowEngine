package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.util.Color

object EditorTheme {
    val bg = Color("1e1f22ff") // Темный фон редактора
    val gutterBg = Color("2b2d30ff") // Фон номеров строк
    val gutterText = Color("4e5157ff") // Цвет цифр
    val currentLineBg = Color("26282e") // Подсветка текущей строки (чуть светлее фона)
    val selection = Color("214283ff") // Цвет выделения (синий, как в IDE)
    val caret = Color("ced0d6ff") // Цвет каретки
    val indentGuide = Color("393b40ff") // Линии отступов

    object Scrollbar {
        val trackColor = Color("ced0d6").withAlpha(0f)
        val trackHover = Color("ced0d6").withAlpha(0.2f)

        val color = Color("ffffff20")
        val hoverColor = Color("ffffff40")
    }

    object Popup {
        val bg = Color("2b2d30ff")
        val border = Color("393b40ff")
        val selectedBg = Color("0d293eff") // Темно-синий для выбранного элемента
        val textPrimary = Color("dfe1e5ff")
        val textMatch = Color("579bfaff") // Голубой для совпадений
        val textDim = Color("6f737aff") // Серый для деталей

        object Tag {
            val function = Color("c77dbbff")
            val property = Color("e6a66cff")
            val type = Color("ebda79ff")
            val localVariable = Color("68a1e0ff")
            val keyword = Color("cfd2d6ff")
        }
    }
}