package ru.hollowhorizon.hollowengine.client.gui.scripting

import de.fabmax.kool.util.Color
import ru.hollowhorizon.hollowengine.client.gui.colors.ColorTheme

object EditorTheme {
    val bg = ColorTheme.UI.BackgroundGeneral // Темный фон редактора
    val gutterBg = ColorTheme.UI.BackgroundSecondary // Фон номеров строк
    val gutterText = ColorTheme.CodeWindow.LineNumbers // Цвет цифр
    val currentLineBg = ColorTheme.UI.BackgroundElements // Подсветка текущей строки
    val selection = ColorTheme.CodeWindow.Selection // Цвет выделения
    val caret = ColorTheme.UI.WhiteReplacement // Цвет каретки
    val indentGuide = ColorTheme.UI.BackgroundAccent.withAlpha(0.3f) // Линии отступов

    object Scrollbar {
        val trackColor = ColorTheme.UI.BackgroundSecondary.withAlpha(0f)
        val trackHover = ColorTheme.UI.BackgroundElements.withAlpha(0.2f)

        val color = ColorTheme.UI.BackgroundAccent.withAlpha(0.5f)
        val hoverColor = ColorTheme.UI.WhiteReplacement.withAlpha(0.7f)
    }

    object Popup {
        val bg = ColorTheme.UI.BackgroundElements // Фон попапа
        val border = ColorTheme.UI.BackgroundAccent // Граница попапа
        val selectedBg = ColorTheme.Accents.Main.withAlpha(0.2f) // Фон выбранного элемента
        val textPrimary = ColorTheme.UI.WhiteReplacement // Основной текст
        val textMatch = ColorTheme.Accents.Main // Цвет совпадений (акцентный)
        val textDim = ColorTheme.CodeWindow.LineNumbers // Вторичный текст

        object Tag {
            val function = Color("c77dbbff") // Функции (фиолетовый)
            val property = Color("e6a66cff") // Свойства (оранжевый)
            val type = Color("ebda79ff") // Типы (желтый)
            val localVariable = Color("68a1e0ff") // Локальные переменные (голубой)
            val keyword = Color("cfd2d6ff") // Ключевые слова (серый)
        }
    }
}