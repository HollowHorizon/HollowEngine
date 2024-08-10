package ru.hollowhorizon.hollowengine.client.docs

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags
import ru.hollowhorizon.hc.client.imgui.ImGuiMethods
import ru.hollowhorizon.hc.client.utils.rl
import ru.hollowhorizon.hc.client.utils.toTexture

/**
 * English:
 * Utilities special for Docs
 *
 * Russian:
 * Утилиты специально для документации
 */
object DocsUtils {
    private val docsLang = DocsLanguage.getInstance()

    /**
     * @author _BENDY659_ and HollowHorizon
     *
     * > English:
     * Displays the text in of the page
     * @param text ID text fore the translate
     * @param fontSize Text size.The size can only be: 10b 20b 40b 50b 70b 90 or 100
     * @param center Determines whether the text should be centered on the page
     *
     * > Russian:
     * Отображает текст на странице
     * @param textId ID текст для перевода
     * @param fontSize Размер текста. Размер может быть только: 10, 20, 40, 0, 70, 90 или 100
     * @param center Определяет, должен ли текст быть по центру страницы
     */
    fun text(textId: String, fontSize: Int = 30, center: Boolean = true, shadow: Boolean = false) {
        ImGuiMethods.pushFontSize(fontSize) {
            val contextWidth = ImGui.getContentRegionAvailX()
            val text =
                if (docsLang.has(textId)) docsLang.getOrDefault(textId)
                else textId

            val textWidth = ImGui.calcTextSize(text, true, contextWidth).x

            if (center) ImGui.sameLine(contextWidth / 2 - textWidth / 2)
            if (shadow) textShadow(text) else ImGui.textWrapped(text)
        }
    }

    /**
     * @author _BENDY659_ and HollowHorizon
     *
     * > English:
     *
     *
     * > Russian:
     * Показывает изображение как титульник для страницы
     * @param titleId ID титульника. Чтобы изображение появилось корректно, нужно чтобы в пути `hollowengine:docs/titles/` был файл изображения с таким же названием как и название титульника
     * @param customSize Размер титульника. По умолчанию стоит 1920f на 1080f
     * @param titleScale Процент отношения размера титульника от размера страницы
     * Титульник так же имеет hover текст. Чтобы он корректно отображался, нужно в папке перевода `lang/docs/<код_языка>.json` в id текста написать `titles.<имя титульника>.txt`
     */
    fun titleImg(titleId: String, customSize: Array<Float> = arrayOf(1920f, 1080f), titleScale: Float = 1.0f) {
        val (imageWidth, imageHeight) = customSize[0] to customSize[1]
        val imgW = ImGui.getContentRegionAvailX() * titleScale
        val imgH = imgW * imageHeight / imageWidth

        ImGui.setCursorPosX(ImGui.getContentRegionAvailX() / 2 - imgW / 2)
        ImGui.image("hollowengine:docs/titles/$titleId.png".rl.toTexture().id, imgW, imgH)

        val (titleDesc, descExist) =
            if (docsLang.has("${titleId}_desc")) docsLang.getOrDefault("${titleId}_desc") to true
            else titleId to false

        if (ImGui.isItemHovered() && descExist) ImGui.setTooltip(docsLang.getOrDefault(titleDesc))
    }

    val tableSizes = mutableMapOf<String, Float>()

    /**
     * @author _BENDY659_ and Halva
     *
     * > English:
     *
     *
     * > Russian:
     * Красивая рамочка (разноцветная)
     * @param name Название рамочки сверху
     * @param type Тип рамки. Есть: note (серая), info (синяя), warn (жёлтая) и err (красная)
     * @param tableContainer Всё что будет внутри этой рамки
     */
    fun table(name: String, type: TableType = TableType.NOTE, height: Float = 512f, tableContainer: () -> Unit) {
        val borderColor = type.borderColor
        val bgColor = type.backgroundColor

        val iconPath = "hollowengine:docs/icons/table_$type.png".rl.toTexture().id
        val tableWidth = ImGui.getContentRegionAvailX() * 0.9f

        ImGui.pushStyleVar(ImGuiStyleVar.ChildBorderSize, 4f)
        ImGui.pushStyleVar(ImGuiStyleVar.ChildRounding, 16f)
        ImGui.pushStyleColor(ImGuiCol.Border, borderColor[0], borderColor[1], borderColor[2], borderColor[3])
        ImGui.pushStyleColor(ImGuiCol.ChildBg, bgColor[0], bgColor[1], bgColor[2], bgColor[3])

        ImGui.setCursorPosX(ImGui.getContentRegionAvailX() / 2 - tableWidth / 2)
        ImGui.beginChild(
            "##table_$name", tableWidth, tableSizes.computeIfAbsent(name) { 100f }, true,
            ImGuiWindowFlags.NoMove or ImGuiWindowFlags.NoResize
        )

        val startHeight = ImGui.getCursorPosY()

        ImGui.setCursorPosX(0f + 8)
        ImGui.image(
            iconPath,
            58f, 58f,
            0f, 0f, 1f, 1f,
            borderColor[0].toFloat(), borderColor[1].toFloat(), borderColor[2].toFloat(), borderColor[3].toFloat()
        )
        text(name, 40, true, true); ImGui.sameLine()
        ImGui.setCursorPosX(ImGui.getWindowWidth() - 58f - 8)
        ImGui.image(
            iconPath,
            58f, 58f,
            0f, 0f, 1f, 1f,
            borderColor[0].toFloat(), borderColor[1].toFloat(), borderColor[2].toFloat(), borderColor[3].toFloat()
        )

        ImGui.separator()
        ImGui.newLine()

        tableContainer()

        val endHeight = ImGui.getCursorPosY()

        tableSizes[name] = (endHeight - startHeight) + ImGui.getStyle().windowPaddingY * 2

        ImGui.endChild()
        ImGui.popStyleColor(2)
        ImGui.popStyleVar(2)
    }

    /**
     * @author _BENDY659_
     *
     * English: Better separator
     *
     * Russian: Улучшенный сепаратор
     */
    fun dline() {
        ImGui.newLine()
        ImGui.newLine()
        ImGui.separator()
        ImGui.newLine()
        ImGui.newLine()
    }

    /**
     * @author _BENDY659_
     *
     * English: Better Button
     *
     * Russian: Улучшенная кнопка
     */
    fun button(buttonId: String, center: Boolean = true, buttonAction: () -> Unit): Boolean {
        val contextWidth = ImGui.getContentRegionAvailX()
        val textWidth = ImGui.calcTextSize(buttonId).x

        ImGui.newLine()
        if (center) ImGui.sameLine(contextWidth / 2 - textWidth / 2)

        ImGui.pushStyleColor(ImGuiCol.Button, 86, 86, 86, 255)
        ImGui.pushStyleVar(ImGuiStyleVar.FrameRounding, 8f)
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, 24f, 16f)

        val buttonResult = ImGui.button(buttonId)

        ImGui.popStyleVar(2)
        ImGui.popStyleColor()

        ImGui.newLine()

        val (buttonDesc, descExist) =
            if (docsLang.has("${buttonId}_desc")) docsLang.getOrDefault("${buttonId}_desc") to true
            else buttonId to false

        if (ImGui.isItemHovered() && descExist) ImGui.setTooltip(docsLang.getOrDefault(buttonDesc))

        if (buttonResult) {
            buttonAction()
        }

        return buttonResult
    }
}

/* Classes class helpers */

enum class TableType(val borderColor: IntArray, val backgroundColor: IntArray) {
    NOTE(intArrayOf(163, 163, 136, 255), intArrayOf(30, 40, 60, 156)),
    INFO(intArrayOf(50, 120, 207, 255), intArrayOf(7, 27, 96, 156)),
    WARN(intArrayOf(230, 154, 0, 255), intArrayOf(70, 19, 0, 186)),
    ERR(intArrayOf(255, 0, 0, 255), intArrayOf(93, 0, 0, 186))
}